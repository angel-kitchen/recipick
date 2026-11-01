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
// 요청 헤더·본문은 기록하지 않는다(빌링키·Authorization이 들어 있다). 정해 두지 않은 경로는 404 PAYMENT_NOT_FOUND.
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
    private final Map<String, AtomicInteger> postCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> getCounts = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private final AtomicReference<CountDownLatch> postGate = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> postArrival = new AtomicReference<>();

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
        postStubs.clear();
        getStubs.clear();
        postCounts.clear();
        getCounts.clear();
        requests.clear();
    }

    // POST /payments/{id}/billing-key
    public void onBillingKeyPayment(String paymentId, int status, String body) {
        postStubs.put(paymentId, new Stub(status, body));
    }

    public void billingKeyPaymentPaid(String paymentId, LocalDateTime paidAtKst) {
        onBillingKeyPayment(paymentId, 200, "{\"payment\":{\"pgTxId\":\"pgtx_" + paymentId + "\",\"paidAt\":\""
            + iso(paidAtKst) + "\"}}");
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

    // paidAt 자리에 날짜가 아닌 문자열을 넣은 성공 응답(역직렬화 실패 재현). value는 JSON 문자열로 그대로 들어간다
    public void getPaymentWithRawPaidAt(String paymentId, long amount, String rawPaidAt) {
        onGetPayment(paymentId, 200, paymentJson(paymentId, "PAID", amount, "KRW", rawPaidAt, null));
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
        requests.add(method + " " + path);
        exchange.getRequestBody().readAllBytes();
        Stub stub;
        if ("POST".equals(method) && path.startsWith("/payments/") && path.endsWith("/billing-key")) {
            String id = path.substring("/payments/".length(), path.length() - "/billing-key".length());
            postCounts.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            stub = postStubs.getOrDefault(id, new Stub(404, "{\"type\":\"PAYMENT_NOT_FOUND\"}"));
            awaitPostGate();
        } else if ("GET".equals(method) && path.startsWith("/payments/")) {
            String id = path.substring("/payments/".length());
            getCounts.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            stub = getStubs.getOrDefault(id, new Stub(404, "{\"type\":\"PAYMENT_NOT_FOUND\"}"));
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
