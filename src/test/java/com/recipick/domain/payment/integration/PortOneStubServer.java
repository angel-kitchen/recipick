package com.recipick.domain.payment.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// 흐름 테스트용 PortOne V2 흉내(JDK HttpServer). 실제 RestClient·타임아웃·직렬화를 그대로 통과시키려는 것이지 PG를 검증하는 것이
// 아니다 — 결제 ID별로 POST(빌링키 청구)·GET(단건 조회) 응답을 미리 정해 두고 호출 횟수만 센다(I-12 검증).
// 요청 헤더·본문은 기록하지 않는다(빌링키·Authorization이 들어 있다) — 멤버십 환불의 취소 POST만 본문과 멱등 키 헤더를 보관한다
// (금액·reason·키가 매번 같은지 검증, 빌링키·Authorization 없음). 정해 두지 않은 경로는 404 PAYMENT_NOT_FOUND.
// 동시성 테스트용으로 POST 응답을 래치로 붙잡아 둘 수 있다(holdPosts → postArrived 대기 → releasePosts). 붙잡힌 요청이 다른 요청을
// 막지 않게 요청마다 별도 스레드에서 처리한다
public final class PortOneStubServer {

    // 붙잡은 POST가 풀리지 않을 때 스텁이 스스로 응답하는 한도(테스트 쪽 대기 한도보다 짧다)
    private static final long HOLD_LIMIT_SECONDS = 10;

    public record Stub(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Stub> postStubs = new ConcurrentHashMap<>();
    private final Map<String, Stub> getStubs = new ConcurrentHashMap<>();
    private final Map<String, Stub> billingKeyStubs = new ConcurrentHashMap<>();
    private final Map<String, Stub> cancelStubs = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> postCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> getCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> cancelCounts = new ConcurrentHashMap<>();
    // 취소 POST 본문(결제 ID별, 도착 순). 멱등 키·금액·reason이 매번 같은지 검증하는 데 쓴다 — 빌링키·인증은 들어 있지 않다
    private final Map<String, List<String>> cancelBodies = new ConcurrentHashMap<>();
    private final Map<String, List<String>> cancelIdempotencyKeys = new ConcurrentHashMap<>();
    private final AtomicReference<Long> cancelDelayMillis = new AtomicReference<>();
    // 결제 ID별 취소 시뮬레이션(멤버십 환불 흐름용). 취소 POST 본문의 금액·reason으로 취소 내역을 만들어 보관하고 GET이 그 목록·누적액을
    // 돌려준다. 같은 멱등 키의 재POST는 같은 취소 내역을 돌려준다(PortOne 멱등 동작). 명시 스텁(onGetPayment·onCancel)이 있으면 그것이 우선
    private final Map<String, Simulation> simulations = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private final AtomicReference<CountDownLatch> postGate = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> postArrival = new AtomicReference<>();
    // 미리 정하지 않은 결제 ID에 줄 기본 결과. 스케줄러·카드 등록 재개처럼 실행 중에 새 결제 ID가 생겨 테스트가 ID를 미리 모를 때 쓴다
    private final AtomicReference<DefaultResult> defaultResult = new AtomicReference<>();

    private record DefaultResult(boolean paid, long amount, LocalDateTime paidAtKst) {
    }

    public PortOneStubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("PortOne 스텁 서버를 열 수 없음", e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(handlers);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        releasePosts();
        server.stop(0);
        handlers.shutdownNow();
    }

    // 다음 POST부터 releasePosts()까지 응답을 붙잡는다. 돌려준 래치는 첫 POST가 도착하면 열린다
    public CountDownLatch holdPosts() {
        CountDownLatch arrived = new CountDownLatch(1);
        postArrival.set(arrived);
        postGate.set(new CountDownLatch(1));
        return arrived;
    }

    public void releasePosts() {
        CountDownLatch gate = postGate.getAndSet(null);
        if (gate != null) {
            gate.countDown();
        }
    }

    public void reset() {
        releasePosts();
        postArrival.set(null);
        defaultResult.set(null);
        postStubs.clear();
        getStubs.clear();
        billingKeyStubs.clear();
        cancelStubs.clear();
        postCounts.clear();
        getCounts.clear();
        cancelCounts.clear();
        cancelBodies.clear();
        cancelIdempotencyKeys.clear();
        cancelDelayMillis.set(null);
        simulations.clear();
        requests.clear();
    }

    // ---------- 취소 시뮬레이션 ----------

    public static final class SimulatedCancellation {
        private final String id;
        private volatile String status;
        private final long totalAmount;
        private final String reason;
        private final LocalDateTime requestedAt;

        SimulatedCancellation(String id, String status, long totalAmount, String reason, LocalDateTime requestedAt) {
            this.id = id;
            this.status = status;
            this.totalAmount = totalAmount;
            this.reason = reason;
            this.requestedAt = requestedAt;
        }

        public String id() {
            return id;
        }

        public String status() {
            return status;
        }

        public long totalAmount() {
            return totalAmount;
        }

        public String reason() {
            return reason;
        }
    }

    private static final class Simulation {
        final String storeId;
        final long amount;
        final LocalDateTime paidAt;
        volatile String postStatus = "SUCCEEDED";
        volatile boolean postWithoutId = false;
        final List<SimulatedCancellation> cancellations = new CopyOnWriteArrayList<>();
        final Map<String, SimulatedCancellation> byIdempotencyKey = new ConcurrentHashMap<>();
        final AtomicInteger sequence = new AtomicInteger();

        Simulation(String storeId, long amount, LocalDateTime paidAt) {
            this.storeId = storeId;
            this.amount = amount;
            this.paidAt = paidAt;
        }
    }

    // 이 결제 ID의 GET·취소 POST를 시뮬레이션한다. POST는 기본 SUCCEEDED 취소 내역을 만든다
    public void simulateRefunds(String paymentId, String storeId, long amount, LocalDateTime paidAtKst) {
        simulations.put(paymentId, new Simulation(storeId, amount, paidAtKst));
    }

    // 다음 취소 POST가 만들 취소 내역의 상태(SUCCEEDED·REQUESTED·FAILED)
    public void simulatePostStatus(String paymentId, String status) {
        simulation(paymentId).postStatus = status;
    }

    // 다음 취소 POST 응답에서 취소 ID를 빼고 돌려준다(응답은 결과 불명이지만 PG에는 취소가 만들어진 상황)
    public void simulatePostWithoutId(String paymentId, boolean withoutId) {
        simulation(paymentId).postWithoutId = withoutId;
    }

    // 보관된 취소 내역의 상태를 바꾼다(REQUESTED → SUCCEEDED 등). GET이 바로 반영한다
    public void settleSimulated(String paymentId, String cancellationId, String status) {
        simulation(paymentId).cancellations.stream().filter(c -> c.id.equals(cancellationId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("없는 시뮬레이션 취소: " + cancellationId)).status = status;
    }

    // 외부(콘솔 등)에서 생긴 취소를 보관 목록에 넣는다 — 서버 생성 reason이 아닌 취소
    public SimulatedCancellation addSimulatedCancellation(String paymentId, String status, long totalAmount, String reason,
                                                          LocalDateTime requestedAtKst) {
        return addSimulatedCancellation(paymentId, status, totalAmount, reason, requestedAtKst, "CONSOLE");
    }

    // trigger를 지정해 넣는다(API면 서버 생성 reason의 중복 취소 재현)
    public SimulatedCancellation addSimulatedCancellation(String paymentId, String status, long totalAmount, String reason,
                                                          LocalDateTime requestedAtKst, String trigger) {
        Simulation sim = simulation(paymentId);
        String prefix = "API".equals(trigger) ? "cxl_api_" : "cxl_ext_";
        SimulatedCancellation c = new SimulatedCancellation(prefix + sim.sequence.incrementAndGet(), status,
            totalAmount, reason, requestedAtKst);
        sim.cancellations.add(c);
        return c;
    }

    // 명시 취소 스텁을 지워 시뮬레이션으로 돌아간다
    public void clearCancelStub(String paymentId) {
        cancelStubs.remove(paymentId);
    }

    public void clearGetStub(String paymentId) {
        getStubs.remove(paymentId);
    }

    public List<SimulatedCancellation> simulatedCancellations(String paymentId) {
        return List.copyOf(simulation(paymentId).cancellations);
    }

    private Simulation simulation(String paymentId) {
        Simulation sim = simulations.get(paymentId);
        if (sim == null) {
            throw new IllegalStateException("시뮬레이션이 없는 결제 ID: " + paymentId);
        }
        return sim;
    }

    private Stub simulatedCancel(Simulation sim, String idempotencyKey, String body) {
        synchronized (sim) {
            SimulatedCancellation existing = sim.byIdempotencyKey.get(idempotencyKey);
            SimulatedCancellation c = existing;
            if (c == null) {
                long amount = Long.parseLong(jsonNumber(body, "amount"));
                String reason = jsonString(body, "reason");
                c = new SimulatedCancellation("cxl_" + sim.sequence.incrementAndGet(), sim.postStatus, amount, reason,
                    LocalDateTime.of(2027, 3, 10, 15, 0).plusSeconds(sim.sequence.get()));
                sim.cancellations.add(c);
                sim.byIdempotencyKey.put(idempotencyKey, c);
            }
            String json = sim.postWithoutId
                ? "{\"cancellation\":{\"status\":\"" + c.status + "\",\"totalAmount\":" + c.totalAmount + "}}"
                : "{\"cancellation\":" + simulatedCancellationJson(c) + "}";
            return new Stub(200, json);
        }
    }

    private Stub simulatedGet(String paymentId, Simulation sim) {
        long cancelled = sim.cancellations.stream().filter(c -> "SUCCEEDED".equals(c.status))
            .mapToLong(c -> c.totalAmount).sum();
        String status = cancelled <= 0 ? "PAID" : (cancelled >= sim.amount ? "CANCELLED" : "PARTIAL_CANCELLED");
        StringBuilder cancellations = new StringBuilder("[");
        for (int i = 0; i < sim.cancellations.size(); i++) {
            if (i > 0) {
                cancellations.append(',');
            }
            cancellations.append(simulatedCancellationJson(sim.cancellations.get(i)));
        }
        cancellations.append(']');
        return new Stub(200, "{\"id\":\"" + paymentId + "\",\"transactionId\":\"tx_" + paymentId
            + "\",\"storeId\":\"" + sim.storeId + "\",\"status\":\"" + status + "\",\"amount\":{\"total\":" + sim.amount
            + ",\"paid\":" + sim.amount + ",\"cancelled\":" + cancelled + "},\"currency\":\"KRW\",\"paidAt\":\""
            + iso(sim.paidAt) + "\"" + (sim.cancellations.isEmpty() ? "" : ",\"cancellations\":" + cancellations) + "}");
    }

    private static String simulatedCancellationJson(SimulatedCancellation c) {
        String trigger = c.id.startsWith("cxl_ext_") ? "CONSOLE" : "API";
        return cancellationJson(c.id, c.status, c.totalAmount, c.reason, trigger, c.requestedAt,
            "SUCCEEDED".equals(c.status) ? c.requestedAt.plusSeconds(3) : null);
    }

    private static String jsonNumber(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("취소 본문에 " + field + " 없음");
        }
        return m.group(1);
    }

    private static String jsonString(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("취소 본문에 " + field + " 없음");
        }
        return m.group(1);
    }

    // POST /payments/{id}/billing-key
    public void onBillingKeyPayment(String paymentId, int status, String body) {
        postStubs.put(paymentId, new Stub(status, body));
    }

    public void billingKeyPaymentPaid(String paymentId, LocalDateTime paidAtKst) {
        onBillingKeyPayment(paymentId, 200, "{\"payment\":{\"pgTxId\":\"pgtx_" + paymentId + "\",\"paidAt\":\""
            + iso(paidAtKst) + "\"}}");
    }

    // 정하지 않은 결제 ID의 POST는 PAID 응답, GET은 같은 ID의 KRW PAID(paidAt 고정)
    public void defaultPaid(long amount, LocalDateTime paidAtKst) {
        defaultResult.set(new DefaultResult(true, amount, paidAtKst));
    }

    // 정하지 않은 결제 ID의 POST는 거절 응답, GET은 같은 ID의 FAILED
    public void defaultFailed(long amount) {
        defaultResult.set(new DefaultResult(false, amount, null));
    }

    // 기본 결과를 지운다. 이후 정하지 않은 결제 ID는 POST·GET 모두 404(결과 불명)
    public void clearDefault() {
        defaultResult.set(null);
    }

    // GET /billing-keys/{billingKey}. 카드 등록 API 흐름용 — 검증기가 통과하는 ISSUED 카드 응답(상점·채널·발급 요청·고객 ID 일치).
    // 빌링키 원문은 응답 본문에만 들어가고 기록 목록에는 남지 않는다
    public void billingKeyIssued(String billingKey, String storeId, String channelKey, String issueId, Long memberId,
                                 LocalDateTime issuedAtKst) {
        billingKeyStubs.put(billingKey, new Stub(200, "{\"status\":\"ISSUED\",\"billingKey\":\"" + billingKey
            + "\",\"storeId\":\"" + storeId + "\",\"channels\":[{\"type\":\"TEST\",\"key\":\"" + channelKey
            + "\"}],\"customer\":{\"id\":\"" + memberId + "\"},"
            + "\"methods\":[{\"type\":\"BillingKeyPaymentMethodCard\"}],\"issueId\":\"" + issueId
            + "\",\"issuedAt\":\"" + iso(issuedAtKst) + "\"}"));
    }

    // GET /billing-keys/{billingKey} 응답을 직접 정한다(정합화 조회의 404 유형·권한 오류·장애 재현)
    public void onBillingKeyLookup(String billingKey, int status, String body) {
        billingKeyStubs.put(billingKey, new Stub(status, body));
    }

    // PortOne이 삭제됐다고 답하는 키
    public void billingKeyDeleted(String billingKey) {
        onBillingKeyLookup(billingKey, 200, "{\"status\":\"DELETED\",\"billingKey\":\"" + billingKey
            + "\",\"deletedAt\":\"2027-02-27T00:00:00Z\"}");
    }

    public int billingKeyLookupCount() {
        return (int) requests.stream().filter("GET /billing-keys/[REDACTED]"::equals).count();
    }

    // GET /payments/{id}
    public void onGetPayment(String paymentId, int status, String body) {
        getStubs.put(paymentId, new Stub(status, body));
    }

    public void getPaymentPaid(String paymentId, long amount, String currency, LocalDateTime paidAtKst) {
        onGetPayment(paymentId, 200, paymentJson(paymentId, "PAID", amount, currency, iso(paidAtKst), null));
    }

    public void getPaymentFailed(String paymentId, long amount) {
        onGetPayment(paymentId, 200, paymentJson(paymentId, "FAILED", amount, "KRW", null,
            "{\"summary\":\"declined\"}"));
    }

    // 멤버십 환불 정합화용 PAID 응답 — 상점 ID·누적 취소액·취소 내역(id·status·totalAmount·reason·trigger)을 함께 준다.
    // cancellationsJson은 "[{...},{...}]" 그대로(없으면 null → 배열 없음, cancelled 0)
    public void getPaymentPaidWithCancellations(String paymentId, String storeId, long amount, LocalDateTime paidAtKst,
                                                long cancelledAmount, String cancellationsJson) {
        String status = cancelledAmount <= 0 ? "PAID" : (cancelledAmount >= amount ? "CANCELLED" : "PARTIAL_CANCELLED");
        onGetPayment(paymentId, 200, "{\"id\":\"" + paymentId + "\",\"transactionId\":\"tx_" + paymentId
            + "\",\"storeId\":\"" + storeId + "\",\"status\":\"" + status + "\",\"amount\":{\"total\":" + amount
            + ",\"paid\":" + amount + ",\"cancelled\":" + cancelledAmount + "},\"currency\":\"KRW\",\"paidAt\":\""
            + iso(paidAtKst) + "\"" + (cancellationsJson == null ? "" : ",\"cancellations\":" + cancellationsJson) + "}");
    }

    // 취소 내역 JSON 한 건. canceledAtKst가 null이면 cancelledAt 없이(SUCCEEDED여도 비어 있을 수 있다)
    public static String cancellationJson(String id, String status, long totalAmount, String reason, String trigger,
                                          LocalDateTime requestedAtKst, LocalDateTime canceledAtKst) {
        return "{\"id\":\"" + id + "\",\"status\":\"" + status + "\",\"totalAmount\":" + totalAmount
            + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
            + ",\"trigger\":\"" + trigger + "\""
            + (requestedAtKst == null ? "" : ",\"requestedAt\":\"" + iso(requestedAtKst) + "\"")
            + (canceledAtKst == null ? "" : ",\"cancelledAt\":\"" + iso(canceledAtKst) + "\"") + "}";
    }

    // POST /payments/{id}/cancel 응답을 정한다(멤버십 환불 금액 지정 취소)
    public void onCancel(String paymentId, int status, String body) {
        cancelStubs.put(paymentId, new Stub(status, body));
    }

    public void cancelSucceeded(String paymentId, String cancellationId, long totalAmount, String reason,
                                LocalDateTime canceledAtKst) {
        onCancel(paymentId, 200, "{\"cancellation\":" + cancellationJson(cancellationId, "SUCCEEDED", totalAmount,
            reason, "API", canceledAtKst, canceledAtKst) + "}");
    }

    public void cancelRequested(String paymentId, String cancellationId, long totalAmount, String reason,
                                LocalDateTime requestedAtKst) {
        onCancel(paymentId, 200, "{\"cancellation\":" + cancellationJson(cancellationId, "REQUESTED", totalAmount,
            reason, "API", requestedAtKst, null) + "}");
    }

    public void cancelError(String paymentId, int status, String type) {
        onCancel(paymentId, status, "{\"type\":\"" + type + "\",\"message\":\"stub\"}");
    }

    // 취소 POST 응답을 millis만큼 늦춘다(읽기 timeout 재현)
    public void delayCancels(long millis) {
        cancelDelayMillis.set(millis);
    }

    public int cancelCount(String paymentId) {
        return cancelCounts.getOrDefault(paymentId, new AtomicInteger()).get();
    }

    public int totalCancelCount() {
        return cancelCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public List<String> cancelBodies(String paymentId) {
        return List.copyOf(cancelBodies.getOrDefault(paymentId, List.of()));
    }

    public List<String> cancelIdempotencyKeys(String paymentId) {
        return List.copyOf(cancelIdempotencyKeys.getOrDefault(paymentId, List.of()));
    }

    // paidAt 자리에 날짜가 아닌 문자열을 넣은 성공 응답(역직렬화 실패 재현). value는 JSON 문자열로 그대로 들어간다
    public void getPaymentWithRawPaidAt(String paymentId, long amount, String rawPaidAt) {
        onGetPayment(paymentId, 200, paymentJson(paymentId, "PAID", amount, "KRW", rawPaidAt, null));
    }

    // 거래는 있지만 아직 끝나지 않은 상태(PENDING·READY 등). paidAt 없음
    public void getPaymentInStatus(String paymentId, String status, long amount) {
        onGetPayment(paymentId, 200, paymentJson(paymentId, status, amount, "KRW", null, null));
    }

    // 조회 장애(5xx) — 거래의 존재도 부재도 알 수 없다
    public void getPaymentServerError(String paymentId) {
        onGetPayment(paymentId, 500, "{\"type\":\"INTERNAL_SERVER_ERROR\"}");
    }

    public void getPaymentNotFound(String paymentId) {
        onGetPayment(paymentId, 404, "{\"type\":\"PAYMENT_NOT_FOUND\"}");
    }

    public int postCount(String paymentId) {
        return postCounts.getOrDefault(paymentId, new AtomicInteger()).get();
    }

    public int getCount(String paymentId) {
        return getCounts.getOrDefault(paymentId, new AtomicInteger()).get();
    }

    public int totalPostCount() {
        return postCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public int totalGetCount() {
        return getCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public int totalRequests() {
        return requests.size();
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        // 빌링키 조회 경로에는 키 원문이 들어 있어 경로를 기록하지 않는다
        requests.add(method + " " + (path.startsWith("/billing-keys/") ? "/billing-keys/[REDACTED]" : path));
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        Stub stub;
        if ("POST".equals(method) && path.startsWith("/payments/") && path.endsWith("/cancel")) {
            String id = path.substring("/payments/".length(), path.length() - "/cancel".length());
            cancelCounts.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            cancelBodies.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>())
                .add(new String(requestBody, StandardCharsets.UTF_8));
            String idempotencyKey = String.valueOf(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            cancelIdempotencyKeys.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(idempotencyKey);
            awaitPostGate();
            Stub explicit = cancelStubs.get(id);
            Simulation sim = simulations.get(id);
            if (explicit != null) {
                stub = explicit;
            } else if (sim != null) {
                stub = simulatedCancel(sim, idempotencyKey, new String(requestBody, StandardCharsets.UTF_8));
            } else {
                stub = new Stub(404, "{\"type\":\"PAYMENT_NOT_FOUND\"}");
            }
            Long delay = cancelDelayMillis.get();
            if (delay != null) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else if ("POST".equals(method) && path.startsWith("/payments/") && path.endsWith("/billing-key")) {
            String id = path.substring("/payments/".length(), path.length() - "/billing-key".length());
            postCounts.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            stub = postStubs.getOrDefault(id, defaultPost(id));
            awaitPostGate();
        } else if ("GET".equals(method) && path.startsWith("/payments/")) {
            String id = path.substring("/payments/".length());
            getCounts.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            Stub explicit = getStubs.get(id);
            Simulation sim = simulations.get(id);
            stub = explicit != null ? explicit : (sim != null ? simulatedGet(id, sim) : defaultGet(id));
        } else if ("GET".equals(method) && path.startsWith("/billing-keys/")) {
            String key = path.substring("/billing-keys/".length());
            stub = billingKeyStubs.getOrDefault(key, new Stub(404, "{\"type\":\"BILLING_KEY_NOT_FOUND\"}"));
        } else {
            stub = new Stub(404, "{\"type\":\"NOT_FOUND\"}");
        }
        byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(stub.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Stub defaultPost(String paymentId) {
        DefaultResult result = defaultResult.get();
        if (result == null) {
            return new Stub(404, "{\"type\":\"PAYMENT_NOT_FOUND\"}");
        }
        if (!result.paid()) {
            return new Stub(400, "{\"type\":\"PG_PROVIDER\"}");
        }
        return new Stub(200, "{\"payment\":{\"pgTxId\":\"pgtx_" + paymentId + "\",\"paidAt\":\""
            + iso(result.paidAtKst()) + "\"}}");
    }

    private Stub defaultGet(String paymentId) {
        DefaultResult result = defaultResult.get();
        if (result == null) {
            return new Stub(404, "{\"type\":\"PAYMENT_NOT_FOUND\"}");
        }
        if (!result.paid()) {
            return new Stub(200, paymentJson(paymentId, "FAILED", result.amount(), "KRW", null,
                "{\"summary\":\"declined\"}"));
        }
        return new Stub(200, paymentJson(paymentId, "PAID", result.amount(), "KRW", iso(result.paidAtKst()), null));
    }

    private void awaitPostGate() {
        CountDownLatch arrived = postArrival.get();
        if (arrived != null) {
            arrived.countDown();
        }
        CountDownLatch gate = postGate.get();
        if (gate == null) {
            return;
        }
        try {
            gate.await(HOLD_LIMIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String paymentJson(String id, String status, Long amount, String currency, String paidAt,
                                      String failure) {
        return "{\"id\":\"" + id + "\",\"transactionId\":\"tx_" + id + "\",\"status\":\"" + status + "\""
            + (amount == null ? "" : ",\"amount\":{\"total\":" + amount + ",\"paid\":" + amount + "}")
            + (currency == null ? "" : ",\"currency\":\"" + currency + "\"")
            + (paidAt == null ? "" : ",\"paidAt\":\"" + paidAt + "\"")
            + (failure == null ? "" : ",\"failure\":" + failure)
            + "}";
    }

    // KST LocalDateTime → UTC ISO-8601(PortOne은 UTC 'Z'로 준다)
    private static String iso(LocalDateTime kst) {
        return OffsetDateTime.of(kst, ZoneOffset.ofHours(9)).withOffsetSameInstant(ZoneOffset.UTC).toString();
    }
}
