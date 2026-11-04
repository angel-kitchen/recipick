package com.recipick.domain.payment.facade;

import java.sql.SQLException;

// 잠금 실패 로그에 남길 DB 오류 번호. 교착과 잠슴대기 초과를 예외 종류만으로는 가리기 어려워 원인 SQLException의 번호를 그대로 남긴다
// ㅡ CI·운영 로그만으로 어느 쪽이었는지 좁히기 위해서
final class LockFailureDetail {

    private LockFailureDetail() {
    }

    static String of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return "errorCode=" + sqlException.getErrorCode() + ", sqlState=" + sqlException.getSQLState();
            }
        }
        return "errorCode=unknown";
    }
}
