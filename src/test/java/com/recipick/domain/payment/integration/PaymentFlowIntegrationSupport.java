package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.OrderItem;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import com.recipick.domain.order.repository.OrderRepository;
import com.recipick.domain.order.repository.OrderStatusHistoryRepository;
import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.entity.Refund;
import com.recipick.domain.payment.entity.WebhookEvent;
import com.recipick.domain.payment.entity.enums.PaymentMethod;
import com.recipick.domain.payment.entity.enums.PaymentStatus;
import com.recipick.domain.payment.repository.PaymentRepository;
import com.recipick.domain.payment.repository.RefundRepository;
import com.recipick.domain.payment.repository.WebhookEventRepository;
import com.recipick.domain.payment.scheduler.PaymentDelayQueueWorker;
import com.recipick.domain.payment.scheduler.PaymentFallbackScheduler;
import com.recipick.domain.payment.service.PaymentDelayQueue;
import com.recipick.domain.payment.service.PaymentDelayTask;
import com.recipick.domain.payment.service.PaymentDelayTaskType;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.infra.portone.PortOnePaymentClient;
import com.recipick.infra.portone.PortOneWebhookProperties;
import com.recipick.infra.portone.dto.PortOneCancellation;
import com.recipick.infra.portone.dto.PortOneCancellationStatus;
import com.recipick.infra.portone.dto.PortOneCancellationTrigger;
import com.recipick.infra.portone.dto.PortOnePayment;
import com.recipick.infra.portone.dto.PortOnePaymentStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

// 7단계 통합 테스트의 공통 준비물. 웹훅 수신 → 큐 → Worker → 결제·환불 반영을 실제 MySQL·Redis로 잇는다.
// 트랜잭션을 걸지 않는다 — Worker·Facade가 각자 커밋하므로 테스트가 만든 행을 @AfterEach에서 직접 지운다.
// 큐 키는 JVM 전체가 함께 쓰므로 테스트가 만든 작업은 자기 member만 지운다. 예외로, 주문 통합 테스트가 결제 생성 커밋 뒤
// 남긴 PAYMENT_EXPIRE 작업은 이 테스트와의 소유 관계를 알 수 없어 각 테스트 시작 전에 종류별로 정리한다.
// Worker는 실행 시각이 된 작업을 모두 꺼내므로, poll 전에 다른 테스트의 작업이 섞여 있지 않은지 먼저 확인한다(pollOnly)
// — 남의 행을 조용히 처리해 오염시키지 않기 위해서다.
// 시각은 모두 Clock 빈 기준으로 만든다 — 테스트 JVM 시간대가 Clock(Asia/Seoul)과 다를 때 기준이 어긋나지 않게 하기 위해서다
abstract class PaymentFlowIntegrationSupport extends IntegrationTestSupport {

    protected static final String STORE_ID = "test-store-id";
    protected static final long AMOUNT = 23800L;
    // 주문 생성 때 이미 차감된 뒤의 재고와 주문 수량. 환불 완료면 7, 실패·대기면 5 그대로다
    protected static final int STOCK_AFTER_ORDER = 5;
    protected static final int ORDER_QUANTITY = 2;
    protected static final String CANCEL_REASON = "단순 변심";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PAYMENT_EXPIRE_PREFIX = PaymentDelayTaskType.PAYMENT_EXPIRE.name() + ":";

    // PENDING_PAYMENT 주문은 회원당 하나(pending_member_id UNIQUE)라 픽스처마다 회원 ID를 새로 쓴다
    private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong(92_000L);

    @MockitoBean
    protected PortOnePaymentClient portOnePaymentClient;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected PortOneWebhookProperties webhookProperties;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected RefundRepository refundRepository;
    @Autowired
    protected WebhookEventRepository webhookEventRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected OrderItemRepository orderItemRepository;
    @Autowired
    protected OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected MartRepository martRepository;
    @Autowired
    protected PaymentDelayQueue paymentDelayQueue;
    @Autowired
    protected PaymentDelayQueueWorker worker;
    @Autowired
    protected PaymentFallbackScheduler fallbackScheduler;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected Clock clock;

    private final List<Long> martIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> paymentIds = new ArrayList<>();
    private final List<String> portonePaymentIds = new ArrayList<>();

    // 주문을 만드는 다른 통합 테스트가 실제 시각 +5분으로 남긴 만료 작업이 이 테스트의 pollOnly·Worker에 섞이지 않게 한다.
    // 이 시점에는 이 테스트의 픽스처가 아직 없어 자기 작업을 지울 일이 없다. Redis 전체를 비우지 않고 이 종류만 지운다.
    // 테스트를 병렬로 돌리게 되면 이 정리 방식은 다시 검토해야 한다
    @BeforeEach
    void removePaymentExpireTasksFromOtherTests() {
        Set<String> members = redis.opsForZSet().range(PaymentDelayQueue.QUEUE_KEY, 0, -1);
        if (members == null || members.isEmpty()) {
            return;
        }
        members.stream()
            .filter(member -> member.startsWith(PAYMENT_EXPIRE_PREFIX))
            .forEach(member -> redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY, member));
    }

    // 큐 member → 웹훅·환불 → 결제 → 주문 → 상품 → 마트 순으로 지운다(외래키 역순)
    @AfterEach
    void cleanUpFlow() {
        List<WebhookEvent> events = webhookEventRepository.findAll().stream()
            .filter(event -> portonePaymentIds.contains(event.getPortonePaymentId()))
            .toList();
        events.forEach(event -> removeTask(PaymentDelayTask.webhookProcess(event.getId())));
        webhookEventRepository.deleteAll(events);

        if (!paymentIds.isEmpty()) {
            paymentIds.forEach(paymentId -> removeTask(PaymentDelayTask.paymentExpire(paymentId)));
            List<Refund> refunds = refundRepository.findAllByPaymentIdIn(paymentIds);
            refunds.forEach(refund -> {
                removeTask(PaymentDelayTask.refundReconcile(refund.getId()));
                removeTask(PaymentDelayTask.systemRefundExecute(refund.getId()));
                removeTask(PaymentDelayTask.adminRefundExecute(refund.getId()));
            });
            refundRepository.deleteAll(refunds);
            paymentRepository.deleteAllById(paymentIds);
        }
        for (Long orderId : orderIds) {
            orderStatusHistoryRepository.deleteAll(orderStatusHistoryRepository.findAllByOrderId(orderId));
            orderItemRepository.deleteAll(orderItemRepository.findAllByOrderId(orderId));
            orderRepository.deleteById(orderId);
        }
        productRepository.deleteAllById(productIds);
        martRepository.deleteAllById(martIds);

        martIds.clear();
        productIds.clear();
        orderIds.clear();
        paymentIds.clear();
        portonePaymentIds.clear();
    }

    // ---------- 픽스처 ----------

    // 결제 확정 전: PENDING_PAYMENT 주문 + IN_PROGRESS 결제. 승인 웹훅·확정 API가 들어오는 자리다
    protected Flow pendingPayment() {
        long memberId = MEMBER_SEQUENCE.incrementAndGet();
        Mart mart = saveMart();
        Order order = orderRepository.save(Order.builder()
            .memberId(memberId)
            .martId(mart.getId())
            .pendingMemberId(memberId)
            .status(OrderStatus.PENDING_PAYMENT)
            .martStatus(MartStatus.NONE)
            .build());
        orderIds.add(order.getId());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        String portonePaymentId = "pay_flow_" + order.getId();
        Payment payment = paymentRepository.save(Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(portonePaymentId)
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(now.plusMinutes(5))
            .build());
        return track(new Flow(memberId, order.getId(), payment.getId(), portonePaymentId, null, now.minusSeconds(10)));
    }

    // 결제 확정 전 + 재고·품목: 주문 생성으로 재고가 7에서 5로 이미 줄어든 PENDING_PAYMENT 주문 + IN_PROGRESS 결제.
    // 만료·늦은 승인이 재고를 정확히 한 번 되돌리는지 보는 자리다. 만료 시각은 실제 시계 기준으로 호출한 쪽이 정한다
    protected Flow pendingPaymentWithStock(LocalDateTime expiresAt) {
        long memberId = MEMBER_SEQUENCE.incrementAndGet();
        Mart mart = saveMart();
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(STOCK_AFTER_ORDER)
            .deleted(false)
            .build());
        productIds.add(product.getId());
        Order order = orderRepository.save(Order.builder()
            .memberId(memberId)
            .martId(mart.getId())
            .pendingMemberId(memberId)
            .status(OrderStatus.PENDING_PAYMENT)
            .martStatus(MartStatus.NONE)
            .build());
        orderIds.add(order.getId());
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .quantity(ORDER_QUANTITY)
            .build());
        String portonePaymentId = "pay_flow_" + order.getId();
        Payment payment = paymentRepository.save(Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(portonePaymentId)
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(expiresAt)
            .build());
        return track(new Flow(memberId, order.getId(), payment.getId(), portonePaymentId, product.getId(), null));
    }

    // 결제 완료 후: PAID 주문(마트 접수) + PAID 결제 + 재고·품목. 회원 취소가 들어오는 자리다
    protected Flow paidPaymentWithStock() {
        return paidPaymentWithStock(MartStatus.RECEIVED);
    }

    // 결제 완료 후: PAID 주문 + PAID 결제 + 재고·품목. 회원 취소·외부 취소가 들어오는 자리다. 마트 처리 상태는 재고 복구 여부를 가른다
    protected Flow paidPaymentWithStock(MartStatus martStatus) {
        long memberId = MEMBER_SEQUENCE.incrementAndGet();
        Mart mart = saveMart();
        Product product = productRepository.save(Product.builder()
            .mart(mart)
            .name("애호박")
            .totalQuantity(STOCK_AFTER_ORDER)
            .deleted(false)
            .build());
        productIds.add(product.getId());
        Order order = orderRepository.save(Order.builder()
            .memberId(memberId)
            .martId(mart.getId())
            .status(OrderStatus.PAID)
            .martStatus(martStatus)
            .build());
        orderIds.add(order.getId());
        orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .quantity(ORDER_QUANTITY)
            .build());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        String portonePaymentId = "pay_flow_" + order.getId();
        // 결제 완료 상태는 빌더로 못 만들고 실제 전이를 거쳐야 한다
        Payment payment = Payment.builder()
            .orderId(order.getId())
            .paymentMethod(PaymentMethod.CARD)
            .portonePaymentId(portonePaymentId)
            .amount(AMOUNT)
            .status(PaymentStatus.IN_PROGRESS)
            .expiresAt(now.minusMinutes(5))
            .build();
        payment.markPaid("tx_flow_" + order.getId(), now.minusMinutes(8));
        Long paymentId = paymentRepository.save(payment).getId();
        return track(new Flow(memberId, order.getId(), paymentId, portonePaymentId, product.getId(), now.minusMinutes(8)));
    }

    private Mart saveMart() {
        Mart mart = martRepository.save(Mart.builder()
            .regionId(33L)
            .name("웹훅 흐름 테스트 마트")
            .latitude(new BigDecimal("37.3670000"))
            .longitude(new BigDecimal("127.1080000"))
            .serviceRadiusM(2000)
            .build());
        martIds.add(mart.getId());
        return mart;
    }

    private Flow track(Flow flow) {
        paymentIds.add(flow.paymentId());
        portonePaymentIds.add(flow.portonePaymentId());
        return flow;
    }

    // ---------- PortOne 응답 ----------

    // 승인 완료. customData의 주문 ID·금액·완료 시각이 모두 맞는 정상 응답이다
    protected PortOnePayment portOnePaid(Flow flow) {
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(), PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", flow.paidAt(), flow.orderId(), null);
    }

    // 서버가 API로 요청한 전액 취소 하나가 들어 있는 조회 결과. SUCCEEDED면 결제도 CANCELED, 아니면 PAID로 보인다
    protected PortOnePayment portOneWithCancellation(Flow flow, String cancellationId,
                                                     PortOneCancellationStatus status) {
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        boolean succeeded = status == PortOneCancellationStatus.SUCCEEDED;
        PortOneCancellation cancellation = new PortOneCancellation(cancellationId, status, AMOUNT,
            now.minusMinutes(6), succeeded ? now.minusMinutes(1) : null, PortOneCancellationTrigger.API);
        return new PortOnePayment(flow.portonePaymentId(), "tx_flow_" + flow.orderId(),
            succeeded ? PortOnePaymentStatus.CANCELED : PortOnePaymentStatus.PAID,
            AMOUNT, "KRW", flow.paidAt(), flow.orderId(), null, List.of(cancellation));
    }

    // ---------- 웹훅 수신 ----------

    // 실제 서명 → 보안 필터 → Controller → 수신 Facade까지 탄다. cancellationId가 null이면 본문에 넣지 않는다
    protected ResultActions postWebhook(String webhookId, String type, String portonePaymentId,
                                        String cancellationId) throws Exception {
        String cancellation = cancellationId == null ? "" : ",\"cancellationId\":\"" + cancellationId + "\"";
        String body = "{\"type\":\"" + type + "\",\"timestamp\":\"2026-10-26T12:00:00Z\","
            + "\"data\":{\"storeId\":\"" + STORE_ID + "\",\"paymentId\":\"" + portonePaymentId + "\","
            + "\"transactionId\":\"tx_flow\"" + cancellation + "}}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return mockMvc.perform(post("/api/webhooks/portone")
            .header("webhook-id", webhookId)
            .header("webhook-timestamp", timestamp)
            .header("webhook-signature", sign(webhookId, timestamp, body))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    // 검증기와 같은 규칙(Standard Webhooks)으로 서명한다. 키는 application-test.yml의 공개 예제 키다
    private String sign(String webhookId, String timestamp, String body) throws Exception {
        String secret = webhookProperties.secret();
        String encodedKey = secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(encodedKey), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal((webhookId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }

    // 수신 Facade가 저장한 행. 같은 webhook_id는 UNIQUE라 최대 한 건이다
    protected List<WebhookEvent> eventsOf(String webhookId) {
        return webhookEventRepository.findAll().stream()
            .filter(event -> webhookId.equals(event.getWebhookId()))
            .toList();
    }

    protected WebhookEvent reload(WebhookEvent event) {
        return webhookEventRepository.findById(event.getId()).orElseThrow();
    }

    // ---------- 큐 ----------

    // 큐에 있으면 실행 시각(epoch millis), 없으면 null
    protected Double scoreOf(PaymentDelayTask task) {
        return redis.opsForZSet().score(PaymentDelayQueue.QUEUE_KEY, task.member());
    }

    // 예약 시각이 지난 것처럼 만든다. 운영 메서드(ZADD LT)로 지금 시각까지 앞당길 뿐 Redis에 직접 쓰지 않는다
    protected void makeDueNow(PaymentDelayTask task) {
        paymentDelayQueue.enqueueIfEarlier(task, clock.instant());
    }

    protected void removeTask(PaymentDelayTask task) {
        redis.opsForZSet().remove(PaymentDelayQueue.QUEUE_KEY, task.member());
    }

    // 지금 실행할 작업이 이 테스트의 것뿐일 때만 Worker를 한 번 돌린다. 남의 작업이 있으면 처리하지 않고 바로 실패한다
    protected void pollOnly(PaymentDelayTask... expected) {
        Set<String> due = redis.opsForZSet().rangeByScore(
            PaymentDelayQueue.QUEUE_KEY, Double.NEGATIVE_INFINITY, clock.instant().toEpochMilli());
        Set<String> allowed = Arrays.stream(expected).map(PaymentDelayTask::member).collect(Collectors.toSet());
        assertThat(due == null ? Set.<String>of() : due)
            .as("다른 테스트가 남긴 실행 대기 작업이 큐에 있다 — 그 테스트의 정리를 먼저 확인할 것")
            .isSubsetOf(allowed);
        worker.poll();
    }

    // ---------- 시각 ----------

    // 네이티브 UPDATE는 Auditing을 타지 않는다. Fallback·stale 판정이 보는 updated_at을 Clock 기준 과거로 옮긴다
    protected void moveWebhookUpdatedAt(Long webhookEventId, Duration ago) {
        jdbcTemplate.update("UPDATE webhook_event SET updated_at = ? WHERE id = ?",
            LocalDateTime.now(clock).minus(ago), webhookEventId);
    }

    protected void moveRefundRequestedAt(Long refundId, Duration ago) {
        jdbcTemplate.update("UPDATE refund SET requested_at = ? WHERE id = ?",
            LocalDateTime.now(clock).minus(ago), refundId);
    }

    // ---------- 조회 ----------

    protected Payment paymentOf(Flow flow) {
        return paymentRepository.findById(flow.paymentId()).orElseThrow();
    }

    protected Order orderOf(Flow flow) {
        return orderRepository.findById(flow.orderId()).orElseThrow();
    }

    protected List<Refund> refundsOf(Flow flow) {
        return refundRepository.findAllByPaymentIdIn(List.of(flow.paymentId()));
    }

    protected int stockOf(Flow flow) {
        return productRepository.findById(flow.productId()).orElseThrow().getTotalQuantity();
    }

    // ---------- 동시 실행 ----------

    // 모든 스레드가 준비된 뒤 한 번에 출발시켜 같은 행을 두고 실제로 경쟁하게 만든다.
    // 락 대기 초과(5초)보다 넉넉히 기다리고, 한 스레드의 예외는 Future.get이 그대로 던진다
    protected List<Object> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<?> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    // 테스트 하나가 다루는 주문·결제 한 묶음. productId는 결제 완료 픽스처에만 있다
    protected record Flow(Long memberId, Long orderId, Long paymentId, String portonePaymentId, Long productId,
                          LocalDateTime paidAt) {
    }
}
