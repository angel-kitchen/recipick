package com.recipick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recipick.IntegrationTestSupport;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 스키마는 엔티티로 만들어져 옛 평문 컬럼이 없다. 운영 Expand 단계를 재현하려고 컬럼을 잠시 추가했다가 지운다.
// 컨텍스트 기동 때 이미 한 번 돈 러너(컬럼 없음 → 할 일 없음)가 기동을 막지 않았다는 사실 자체가 "컬럼 없는 DB" 검증이다
class BillingKeyCiphertextBackfillerIntegrationTest extends IntegrationTestSupport {

    // 다른 테스트의 회원과 겹치지 않는 범위
    private static final long MEMBER_ID_BASE = 9_000_000L;

    @Autowired
    BillingKeyCiphertextBackfiller backfiller;

    @Autowired
    BillingKeyCipher cipher;

    @Autowired
    BillingKeyFingerprint fingerprint;

    @Autowired
    JdbcTemplate jdbc;

    private long nextMemberId = MEMBER_ID_BASE;

    @BeforeEach
    void addLegacyColumn() {
        jdbc.execute("ALTER TABLE billing_key ADD COLUMN portone_billing_key VARCHAR(255) NULL");
    }

    @AfterEach
    void dropLegacyColumn() {
        jdbc.update("DELETE FROM billing_key WHERE member_id >= ?", MEMBER_ID_BASE);
        jdbc.execute("ALTER TABLE billing_key DROP COLUMN portone_billing_key");
    }

    @Test
    @DisplayName("평문만 있는 행에 암호문·지문을 채우고 평문은 지우지 않는다. 암호문은 원문으로 복호화되고 지문은 원문의 HMAC이다")
    void backfillsPlaintextRows() {
        // given
        List<String> keys = insertPlaintextRows(3);
        Long alreadyEncrypted = insertEncryptedRow("billing-key-test-" + UUID.randomUUID());
        Timestamp before = updatedAtOf(alreadyEncrypted);

        // when
        backfiller.run(null);

        // then
        for (String key : keys) {
            Map<String, Object> row = rowOf(key);
            assertThat(cipher.decrypt((String) row.get("ciphertext"))).isEqualTo(key);
            assertThat(row.get("hash")).isEqualTo(fingerprint.of(key));
            assertThat(row.get("plaintext")).isEqualTo(key);
            assertThat(((String) row.get("ciphertext"))).doesNotContain(key);
        }
        assertThat(updatedAtOf(alreadyEncrypted)).isEqualTo(before);
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("다시 실행해도 이미 채운 행은 건드리지 않는다 — 암호문·updated_at이 그대로다")
    void rerunIsNoOp() {
        // given
        List<String> keys = insertPlaintextRows(2);
        backfiller.run(null);
        List<Map<String, Object>> first = keys.stream().map(this::rowOf).toList();

        // when
        backfiller.run(null);

        // then
        List<Map<String, Object>> second = keys.stream().map(this::rowOf).toList();
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("500건 한도를 넘는 행도 배치를 반복해 모두 채운다")
    void loopsOverBatches() {
        // given
        int count = BillingKeyCiphertextBackfiller.BATCH_SIZE * 2 + 1;
        insertPlaintextRows(count);

        // when
        backfiller.run(null);

        // then
        assertThat(pendingCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE member_id >= ?"
            + " AND portone_billing_key_hash IS NOT NULL", Integer.class, MEMBER_ID_BASE)).isEqualTo(count);
    }

    @Test
    @DisplayName("두 서버가 동시에 실행해도 행마다 유효한 암호문 하나만 남고 둘 다 예외 없이 끝난다")
    void concurrentRunsAreSafe() throws Exception {
        // given
        List<String> keys = insertPlaintextRows(40);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Object> run = () -> {
            backfiller.run(null);
            return null;
        };

        // when
        List<Future<Object>> futures = new ArrayList<>();
        futures.add(executor.submit(run));
        futures.add(executor.submit(run));
        try {
            for (Future<Object> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // then
        for (String key : keys) {
            Map<String, Object> row = rowOf(key);
            assertThat(cipher.decrypt((String) row.get("ciphertext"))).isEqualTo(key);
            assertThat(row.get("hash")).isEqualTo(fingerprint.of(key));
        }
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("채우지 못한 행이 남으면 예외로 기동을 멈춘다 — 같은 평문 두 행은 지문 UNIQUE에 걸린다")
    void failsWhenRowsRemain() {
        // given — 옛 평문 컬럼에는 테스트 스키마에 UNIQUE가 없어 같은 값을 두 번 넣을 수 있다. 배치 한 트랜잭션이 통째로 돌아간다
        String key = "billing-key-test-" + UUID.randomUUID();
        insertPlaintextRow(key);
        insertPlaintextRow(key);

        // when, then
        assertThatThrownBy(() -> backfiller.run(null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(pendingCount()).isEqualTo(2);
    }

    // ---------- 픽스처·조회 ----------

    private List<String> insertPlaintextRows(int count) {
        List<String> keys = new ArrayList<>();
        List<Object[]> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = "billing-key-test-" + UUID.randomUUID();
            keys.add(key);
            args.add(new Object[] {nextMemberId++, key});
        }
        jdbc.batchUpdate("INSERT INTO billing_key(member_id, portone_billing_key, status, issued_at, created_at,"
            + " updated_at) VALUES (?, ?, 'REPLACED', NOW(6), NOW(6), NOW(6))", args);
        return keys;
    }

    private void insertPlaintextRow(String key) {
        jdbc.update("INSERT INTO billing_key(member_id, portone_billing_key, status, issued_at, created_at,"
            + " updated_at) VALUES (?, ?, 'REPLACED', NOW(6), NOW(6), NOW(6))", nextMemberId++, key);
    }

    private Long insertEncryptedRow(String key) {
        long memberId = nextMemberId++;
        jdbc.update("INSERT INTO billing_key(member_id, portone_billing_key_ciphertext, portone_billing_key_hash,"
                + " status, issued_at, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), NOW(6))",
            memberId, cipher.encrypt(key), fingerprint.of(key));
        return jdbc.queryForObject("SELECT id FROM billing_key WHERE member_id = ?", Long.class, memberId);
    }

    private Map<String, Object> rowOf(String key) {
        return jdbc.queryForMap("SELECT portone_billing_key AS plaintext, portone_billing_key_ciphertext AS ciphertext,"
            + " portone_billing_key_hash AS hash, updated_at FROM billing_key WHERE portone_billing_key = ?", key);
    }

    private Timestamp updatedAtOf(Long id) {
        return jdbc.queryForObject("SELECT updated_at FROM billing_key WHERE id = ?", Timestamp.class, id);
    }

    private Integer pendingCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE portone_billing_key_ciphertext IS NULL"
            + " AND portone_billing_key IS NOT NULL", Integer.class);
    }
}
