package com.recipick.domain.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.member.entity.Member;
import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.repository.MemberRepository;
import com.recipick.domain.payment.entity.BillingKey;
import com.recipick.domain.payment.entity.BillingKeyIssueRequest;
import com.recipick.domain.payment.entity.enums.BillingKeyIssueRequestStatus;
import com.recipick.domain.payment.entity.enums.BillingKeyStatus;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.domain.payment.facade.BillingKeyRegistrationProcessor;
import com.recipick.domain.payment.repository.BillingKeyIssueRequestRepository;
import com.recipick.domain.payment.repository.BillingKeyRepository;
import com.recipick.domain.payment.service.BillingKeyRegistration;
import com.recipick.domain.payment.service.BillingKeyRegistrationOutcome;
import com.recipick.domain.payment.service.BillingKeyService;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;

// 빌링키 등록·교체·토큰 규칙·C-10을 실제 MySQL 위에서 본다. PortOne 검증은 Facade가 트랜잭션 밖에서 끝내는 단계라
// 여기서는 그 뒤의 회원 잠금 트랜잭션(Processor)을 직접 부른다 — PG 응답을 흉내 내는 Mock은 두지 않는다(튜터님 지침).
// 실행 SQL은 org.hibernate.SQL 로거를 테스트 중에만 DEBUG로 받아 잠금 문장의 범위를 확인한다
class BillingKeyRegistrationIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Original1!";
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 31, 14, 0);
    private static final Logger SQL_LOGGER = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    // "b1_0.id=?" 같은 PK 단일 조건. 별칭·공백·따옴표 유무에 의존하지 않는다
    private static final Pattern PRIMARY_KEY_CONDITION = Pattern.compile("^(\\w+\\.)?`?id`?\\s*=\\s*\\?$");

    @Autowired
    BillingKeyRegistrationProcessor processor;

    @Autowired
    BillingKeyService billingKeyService;

    @Autowired
    BillingKeyRepository billingKeys;

    @Autowired
    BillingKeyIssueRequestRepository issueRequests;

    @Autowired
    MemberRepository members;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<String> secrets = new ArrayList<>();
    private Member member;
    private ListAppender<ILoggingEvent> sqlAppender;
    private ListAppender<ILoggingEvent> rootAppender;
    private Level previousSqlLevel;

    @BeforeEach
    void setUp() {
        member = saveMember();
        sqlAppender = new ListAppender<>();
        sqlAppender.start();
        previousSqlLevel = SQL_LOGGER.getLevel();
        SQL_LOGGER.setLevel(Level.DEBUG);
        SQL_LOGGER.addAppender(sqlAppender);
        rootAppender = new ListAppender<>();
        rootAppender.start();
        ROOT_LOGGER.addAppender(rootAppender);
    }

    // 발급 요청 → 빌링키 → 회원 순(외래키 역순)으로 지운다
    @AfterEach
    void cleanUp() {
        SQL_LOGGER.detachAppender(sqlAppender);
        SQL_LOGGER.setLevel(previousSqlLevel);
        ROOT_LOGGER.detachAppender(rootAppender);
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM billing_key_issue_request WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM billing_key WHERE member_id = ?", memberId);
            members.deleteById(memberId);
        }
    }

    // ---------- BK-01·02·04: 등록·멱등·교체·재활성화 ----------

    @Test
    @DisplayName("유효한 발급 요청과 새 키를 등록하면 ACTIVE 행 하나가 생기고 요청은 그 키에 USED로 묶인다")
    void registersNewKey() {
        // given
        BillingKeyIssueRequest request = requested();
        String key = newKey();

        // when
        BillingKeyRegistration registration = register(request, key);

        // then
        assertThat(registration.outcome()).isEqualTo(BillingKeyRegistrationOutcome.REGISTERED);
        assertThat(statusOf(key)).isEqualTo("ACTIVE");
        assertThat(countKeysOf(member.getId())).isEqualTo(1L);
        BillingKeyIssueRequest used = issueRequests.findByIssueId(request.getIssueId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(BillingKeyIssueRequestStatus.USED);
        assertThat(used.getUsedAt()).isNotNull();
        assertThat(used.getBillingKey().getId()).isEqualTo(registration.billingKeyId());
        assertThat(used.getBillingKey().getIssuedAt()).isEqualTo(ISSUED_AT);
    }

    @Test
    @DisplayName("같은 요청·같은 키를 다시 보내면 행을 만들지 않고 ALREADY_ACTIVE다")
    void sameKeyIsIdempotent() {
        // given
        BillingKeyIssueRequest request = requested();
        String key = newKey();
        Long firstId = register(request, key).billingKeyId();

        // when
        BillingKeyRegistration again = register(request, key);

        // then
        assertThat(again.outcome()).isEqualTo(BillingKeyRegistrationOutcome.ALREADY_ACTIVE);
        assertThat(again.billingKeyId()).isEqualTo(firstId);
        assertThat(countKeysOf(member.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("새 발급 요청으로 다른 키를 등록하면 이전 키는 REPLACED, 새 키는 ACTIVE다")
    void replacesActiveKey() {
        // given
        String keyA = newKey();
        register(requested(), keyA);
        String keyB = newKey();

        // when
        BillingKeyRegistration registration = register(requested(), keyB);

        // then
        assertThat(registration.outcome()).isEqualTo(BillingKeyRegistrationOutcome.REPLACED);
        assertThat(statusOf(keyA)).isEqualTo("REPLACED");
        assertThat(statusOf(keyB)).isEqualTo("ACTIVE");
        assertThat(countActiveOf(member.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효기간 안의 발급 요청으로 REPLACED 키를 다시 보내면 REACTIVATED이고 현재 ACTIVE 키가 REPLACED가 된다")
    void reactivatesReplacedKeyWithinValidity() {
        // given
        BillingKeyIssueRequest requestA = requested();
        String keyA = newKey();
        register(requestA, keyA);
        String keyB = newKey();
        register(requested(), keyB);

        // when
        BillingKeyRegistration registration = register(requestA, keyA);

        // then
        assertThat(registration.outcome()).isEqualTo(BillingKeyRegistrationOutcome.REACTIVATED);
        assertThat(statusOf(keyA)).isEqualTo("ACTIVE");
        assertThat(statusOf(keyB)).isEqualTo("REPLACED");
        assertThat(countActiveOf(member.getId())).isEqualTo(1L);
        assertThat(countKeysOf(member.getId())).isEqualTo(2L);
    }

    // ---------- BK-03·06': 타인 키·청구 불가 키 ----------

    @Test
    @DisplayName("다른 회원이 등록한 키는 자기 발급 요청이 있어도 BILLING-002이고 행은 바뀌지 않는다")
    void rejectsOtherMembersKey() {
        // given
        String key = newKey();
        register(requested(), key);
        Member other = saveMember();
        BillingKeyIssueRequest othersRequest = requested(other.getId());

        // when
        Throwable thrown = catchThrowable(() -> register(other.getId(), othersRequest, key));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_ALREADY_USED);
        assertThat(ownerOf(key)).isEqualTo(member.getId());
        assertThat(statusOf(key)).isEqualTo("ACTIVE");
        assertThat(issueRequests.findByIssueId(othersRequest.getIssueId()).orElseThrow().getStatus())
            .isEqualTo(BillingKeyIssueRequestStatus.REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = BillingKeyStatus.class, names = {"REVOKED", "DELETE_REQUESTED", "DELETED"})
    @DisplayName("내 키지만 청구 불가 상태면 BILLING-006이다 — 새 카드를 발급받아야 한다")
    void rejectsUnusableKey(BillingKeyStatus status) {
        // given
        String key = newKey();
        saveKey(member.getId(), key, status);

        // when
        Throwable thrown = catchThrowable(() -> register(requested(), key));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_UNUSABLE);
        assertThat(statusOf(key)).isEqualTo(status.name());
    }

    // ---------- 토큰 규칙 ----------

    @Test
    @DisplayName("다른 회원의 발급 요청으로는 등록할 수 없다 — BILLING-007")
    void rejectsOthersIssueRequest() {
        // given
        Member other = saveMember();
        BillingKeyIssueRequest othersRequest = requested(other.getId());

        // when
        Throwable thrown = catchThrowable(() -> register(othersRequest, newKey()));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        assertThat(countKeysOf(member.getId())).isZero();
    }

    @Test
    @DisplayName("만료된 발급 요청의 첫 사용은 BILLING-007이고 행은 생기지 않는다")
    void rejectsExpiredIssueRequest() {
        // given
        BillingKeyIssueRequest expired = requestedAt(member.getId(), LocalDateTime.now(clock).minusMinutes(31));

        // when
        Throwable thrown = catchThrowable(() -> register(expired, newKey()));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        assertThat(countKeysOf(member.getId())).isZero();
        assertThat(issueRequests.findByIssueId(expired.getIssueId()).orElseThrow().getStatus())
            .isEqualTo(BillingKeyIssueRequestStatus.REQUESTED);
    }

    @Test
    @DisplayName("한 키에 쓰인 발급 요청으로 다른 키를 보내면 BILLING-007이고 두 키 모두 바뀌지 않는다")
    void rejectsUsedRequestWithDifferentKey() {
        // given
        BillingKeyIssueRequest request = requested();
        String keyA = newKey();
        register(request, keyA);

        // when
        Throwable thrown = catchThrowable(() -> register(request, newKey()));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        assertThat(statusOf(keyA)).isEqualTo("ACTIVE");
        assertThat(countKeysOf(member.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효기간이 지난 USED 요청이라도 묶인 키가 ACTIVE면 멱등 재요청이라 ALREADY_ACTIVE다")
    void expiredUsedRequestStillIdempotentForActiveKey() {
        // given
        String key = newKey();
        BillingKey active = saveKey(member.getId(), key, BillingKeyStatus.ACTIVE);
        BillingKeyIssueRequest expiredUsed = usedRequestAt(active, LocalDateTime.now(clock).minusMinutes(31));

        // when
        BillingKeyRegistration registration = register(expiredUsed, key);

        // then
        assertThat(registration.outcome()).isEqualTo(BillingKeyRegistrationOutcome.ALREADY_ACTIVE);
        assertThat(registration.billingKeyId()).isEqualTo(active.getId());
    }

    @Test
    @DisplayName("유효기간이 지난 USED 요청으로 REPLACED 키를 되살릴 수는 없다 — BILLING-007, 두 키 모두 그대로")
    void expiredUsedRequestCannotReactivate() {
        // given
        String keyA = newKey();
        BillingKey replaced = saveKey(member.getId(), keyA, BillingKeyStatus.REPLACED);
        String keyB = newKey();
        saveKey(member.getId(), keyB, BillingKeyStatus.ACTIVE);
        BillingKeyIssueRequest expiredUsed = usedRequestAt(replaced, LocalDateTime.now(clock).minusMinutes(31));

        // when
        Throwable thrown = catchThrowable(() -> register(expiredUsed, keyA));

        // then
        assertBusinessError(thrown, PaymentErrorCode.BILLING_KEY_ISSUE_REQUEST_INVALID);
        assertThat(statusOf(keyA)).isEqualTo("REPLACED");
        assertThat(statusOf(keyB)).isEqualTo("ACTIVE");
    }

    // ---------- 회원·트랜잭션 계약 ----------

    @Test
    @DisplayName("탈퇴한 회원은 회원 잠금에서 MEMBER-001로 거절되고 행은 생기지 않는다")
    void rejectsWithdrawnMember() {
        // given
        Member withdrawn = saveMember();
        BillingKeyIssueRequest request = requested(withdrawn.getId());
        withdrawn.withdraw(LocalDateTime.now(clock));
        members.saveAndFlush(withdrawn);

        // when
        Throwable thrown = catchThrowable(() -> register(withdrawn.getId(), request, newKey()));

        // then
        assertBusinessError(thrown, MemberErrorCode.MEMBER_NOT_FOUND);
        assertThat(countKeysOf(withdrawn.getId())).isZero();
    }

    @Test
    @DisplayName("바깥 트랜잭션 없이 서비스를 부르면 거절한다 — 회원 잠금 트랜잭션 안에서만 뜻이 있다")
    void requiresOuterTransaction() {
        // given
        BillingKeyIssueRequest request = requested();

        // when
        Throwable thrown = catchThrowable(
            () -> billingKeyService.register(member.getId(), request.getId(), newKey(), ISSUED_AT));

        // then
        assertThat(thrown).isInstanceOf(IllegalTransactionStateException.class);
    }

    // ---------- BK-09: C-10 ----------

    @Test
    @DisplayName("C-10은 ACTIVE 키가 있을 때만 true다")
    void hasActiveBillingKey() {
        // given
        assertThat(billingKeyService.hasActiveBillingKey(member.getId())).isFalse();
        for (BillingKeyStatus status : List.of(BillingKeyStatus.REPLACED, BillingKeyStatus.REVOKED,
            BillingKeyStatus.DELETE_REQUESTED, BillingKeyStatus.DELETED)) {
            saveKey(member.getId(), newKey(), status);
        }
        assertThat(billingKeyService.hasActiveBillingKey(member.getId())).isFalse();

        // when
        saveKey(member.getId(), newKey(), BillingKeyStatus.ACTIVE);

        // then
        assertThat(billingKeyService.hasActiveBillingKey(member.getId())).isTrue();
    }

    // ---------- SQL·로그 ----------

    @Test
    @DisplayName("첫 등록은 billing_key에 잠금 문장을 내지 않고, 교체는 WHERE 조건이 PK뿐인 잠금만 낸다 — 없는 행·빈 구간을 잠그지 않는다")
    void locksOnlyExistingRowsByPrimaryKey() {
        // given
        String keyA = newKey();

        // when — 첫 등록
        register(requested(), keyA);
        List<String> firstRegistration = capturedSql();
        sqlAppender.list.clear();

        // then — 같은 키·ACTIVE 행 조회는 잠금 없이 나가고, 잠금 조회는 없다
        assertThat(firstRegistration).noneMatch(BillingKeyRegistrationIntegrationTest::locksBillingKey);
        assertThat(firstRegistration).anyMatch(sql -> selectsBillingKey(sql)
            && whereClauseOf(sql).contains("member_id") && !sql.contains("for update"));
        assertThat(firstRegistration).anyMatch(sql -> selectsBillingKey(sql)
            && whereClauseOf(sql).contains("portone_billing_key") && !sql.contains("for update"));

        // when — 교체
        register(requested(), newKey());
        List<String> replacement = capturedSql();

        // then — 잠금 조회의 WHERE는 PK 하나뿐이다(SELECT 목록의 컬럼은 보지 않는다)
        List<String> lockingSql = replacement.stream()
            .filter(BillingKeyRegistrationIntegrationTest::locksBillingKey)
            .toList();
        assertThat(lockingSql).isNotEmpty();
        assertThat(lockingSql).allMatch(sql -> PRIMARY_KEY_CONDITION.matcher(whereClauseOf(sql)).matches());
        assertThat(lockingSql).noneMatch(sql -> whereClauseOf(sql).contains("member_id"));
        assertThat(lockingSql).noneMatch(sql -> whereClauseOf(sql).contains("portone_billing_key"));
    }

    @Test
    @DisplayName("등록 흐름의 로그 어디에도 빌링키 원문과 토큰이 없다")
    void logsNeverContainSecrets() {
        // given
        BillingKeyIssueRequest request = requested();
        String key = newKey();

        // when
        register(request, key);
        register(request, key);
        register(requested(), newKey());

        // then
        List<String> messages = rootAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        for (String secret : secrets) {
            assertThat(messages).noneMatch(message -> message.contains(secret));
            assertThat(rootAppender.list).noneMatch(event -> throwableContains(event.getThrowableProxy(), secret));
        }
    }

    // ---------- 실행 ----------

    private BillingKeyRegistration register(BillingKeyIssueRequest request, String key) {
        return register(member.getId(), request, key);
    }

    private BillingKeyRegistration register(Long memberId, BillingKeyIssueRequest request, String key) {
        return processor.register(memberId, request.getId(), key, ISSUED_AT);
    }

    private static void assertBusinessError(Throwable thrown, ErrorCode errorCode) {
        assertThat(thrown).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    // ---------- 픽스처 ----------

    private Member saveMember() {
        String suffix = UUID.randomUUID().toString();
        Member saved = members.saveAndFlush(Member.builder().email(suffix + "@t.dev").phone(suffix.substring(0, 20))
            .name("빌링키테스트").passwordHash(encoder.encode(PASSWORD)).build());
        memberIds.add(saved.getId());
        return saved;
    }

    // 키 원문·토큰은 로그 검사용으로 모아 둔다
    private String newKey() {
        String key = "billing-key-test-" + UUID.randomUUID();
        secrets.add(key);
        return key;
    }

    private BillingKeyIssueRequest requested() {
        return requested(member.getId());
    }

    private BillingKeyIssueRequest requested(Long memberId) {
        return requestedAt(memberId, LocalDateTime.now(clock));
    }

    private BillingKeyIssueRequest requestedAt(Long memberId, LocalDateTime issuedAt) {
        String issueId = "rcp-bk-" + UUID.randomUUID();
        secrets.add(issueId);
        return issueRequests.saveAndFlush(BillingKeyIssueRequest.requested(memberId, issueId, issuedAt));
    }

    private BillingKeyIssueRequest usedRequestAt(BillingKey key, LocalDateTime issuedAt) {
        BillingKeyIssueRequest request = requestedAt(key.getMemberId(), issuedAt);
        request.markUsed(key, issuedAt.plusMinutes(1));
        return issueRequests.saveAndFlush(request);
    }

    private BillingKey saveKey(Long memberId, String key, BillingKeyStatus status) {
        return billingKeys.saveAndFlush(BillingKey.builder()
            .memberId(memberId).portoneBillingKey(key).status(status).issuedAt(ISSUED_AT).build());
    }

    // ---------- 조회 ----------

    private String statusOf(String key) {
        return jdbc.queryForObject("SELECT status FROM billing_key WHERE portone_billing_key = ?", String.class, key);
    }

    private Long ownerOf(String key) {
        return jdbc.queryForObject("SELECT member_id FROM billing_key WHERE portone_billing_key = ?", Long.class, key);
    }

    private Long countKeysOf(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE member_id = ?", Long.class, memberId);
    }

    private Long countActiveOf(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE member_id = ? AND status = 'ACTIVE'",
            Long.class, memberId);
    }

    // 소문자화·공백 하나로 정규화한 SQL. Hibernate 6은 "from billing_key b1_0 where b1_0.id=? for update" 꼴로 낸다
    private List<String> capturedSql() {
        String thread = Thread.currentThread().getName();
        return sqlAppender.list.stream()
            .filter(event -> thread.equals(event.getThreadName()))
            .map(event -> event.getFormattedMessage().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
            .toList();
    }

    // 로그 이벤트의 예외를 cause·suppressed까지 따라가며 메시지에 문자열이 있는지 본다
    private static boolean throwableContains(IThrowableProxy proxy, String text) {
        if (proxy == null) {
            return false;
        }
        if (proxy.getMessage() != null && proxy.getMessage().contains(text)) {
            return true;
        }
        for (IThrowableProxy suppressed : proxy.getSuppressed()) {
            if (throwableContains(suppressed, text)) {
                return true;
            }
        }
        return throwableContains(proxy.getCause(), text);
    }

    // billing_key 테이블(뒤에 공백)만 — billing_key_issue_request는 제외
    private static boolean selectsBillingKey(String sql) {
        return sql.startsWith("select ") && sql.contains(" from billing_key ");
    }

    private static boolean locksBillingKey(String sql) {
        return selectsBillingKey(sql) && sql.contains(" for update");
    }

    // " where " 뒤부터 " for update"(또는 끝)까지. 별칭·공백은 정규식이 흡수한다
    private static String whereClauseOf(String sql) {
        int where = sql.indexOf(" where ");
        if (where < 0) {
            return "";
        }
        String tail = sql.substring(where + " where ".length());
        int lock = tail.indexOf(" for update");
        return (lock < 0 ? tail : tail.substring(0, lock)).trim();
    }
}
