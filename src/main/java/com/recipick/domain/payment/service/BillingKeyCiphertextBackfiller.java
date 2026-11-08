package com.recipick.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// 옛 평문 컬럼(portone_billing_key)만 있는 행에 암호문·지문을 채운다. 엔티티는 평문 컬럼을 모르므로 JDBC로만 읽고,
// 평문은 지우지 않는다(Contract 전 롤백 때 이전 앱이 읽는다). 요청을 받기 전에 끝나야 해서 ApplicationRunner다 —
// 실패하거나 남은 행이 있으면 예외로 기동을 멈춘다. 조건부 UPDATE라 다시 실행해도, 두 서버가 동시에 실행해도 한 번만 채운다.
// 평문 컬럼이 없는 DB(로컬 신규·테스트·Contract 뒤)에서는 할 일이 없다. 벌크 UPDATE라 updated_at을 함께 갱신한다
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyCiphertextBackfiller implements ApplicationRunner {

    static final int BATCH_SIZE = 500;
    private static final String LEGACY_COLUMN = "portone_billing_key";
    private static final String PENDING_CONDITION =
        "portone_billing_key_ciphertext IS NULL AND portone_billing_key IS NOT NULL";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final BillingKeyCipher cipher;
    private final BillingKeyFingerprint fingerprint;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) {
            return;
        }
        int total = 0;
        Batch batch;
        do {
            batch = transactionTemplate.execute(status -> backfillBatch());
            total += batch == null ? 0 : batch.updated();
        } while (batch != null && batch.selected() > 0);

        Integer remaining = jdbc.queryForObject("SELECT COUNT(*) FROM billing_key WHERE " + PENDING_CONDITION,
            Integer.class);
        if (remaining == null || remaining > 0) {
            throw new IllegalStateException("빌링키 암호문 백필이 끝나지 않았습니다: 남은 행 " + remaining + "건");
        }
        if (total > 0) {
            log.info("빌링키 암호문 백필 완료: {}건", total);
        }
    }

    // 조회된 행을 다른 서버가 먼저 채웠으면 조건부 UPDATE가 0건이라 건너뛴다. 조회가 0건일 때 반복이 끝난다
    private Batch backfillBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, portone_billing_key AS plaintext FROM billing_key WHERE " + PENDING_CONDITION
                + " ORDER BY id LIMIT " + BATCH_SIZE);
        int updated = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (Map<String, Object> row : rows) {
            String plaintext = (String) row.get("plaintext");
            updated += jdbc.update(
                "UPDATE billing_key SET portone_billing_key_ciphertext = ?, portone_billing_key_hash = ?,"
                    + " updated_at = ? WHERE id = ? AND portone_billing_key_ciphertext IS NULL",
                cipher.encrypt(plaintext), fingerprint.of(plaintext), now, row.get("id"));
        }
        return new Batch(rows.size(), updated);
    }

    private boolean legacyColumnExists() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = 'billing_key' AND COLUMN_NAME = ?", Integer.class, LEGACY_COLUMN);
        return count != null && count > 0;
    }

    private record Batch(int selected, int updated) {
    }
}
