package com.recipick.global.audit;

public interface AdminAuditRecorder {

    // 호출자는 업무와 동일한 쓰기 트랜잭션에서 실제 변경·새 실행·개인정보 조회 때만 호출한다.
    void record(AdminAuditCommand command);
}
