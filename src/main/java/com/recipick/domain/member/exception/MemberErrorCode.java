package com.recipick.domain.member.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-001", "회원을 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER-002", "이미 가입된 이메일입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "MEMBER-003", "이미 가입된 휴대전화 번호입니다."),
    REQUIRED_AGREEMENT_MISSING(HttpStatus.BAD_REQUEST, "MEMBER-004", "필수 약관에 동의해 주세요."),

    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "MEMBER-005", "현재 비밀번호가 올바르지 않습니다."),

    WITHDRAW_BLOCKED_ORDER(HttpStatus.CONFLICT, "MEMBER-006", "진행 중인 주문을 먼저 처리해 주세요."),
    WITHDRAW_BLOCKED_MEMBERSHIP(HttpStatus.CONFLICT, "MEMBER-007", "이용 중인 멤버십을 먼저 종료해 주세요."),
    WITHDRAW_SETTLEMENT_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "MEMBER-008", "현재 탈퇴 정산 확인을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    AUDIT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "AUDIT-001", "감사 로그를 찾을 수 없습니다."),

    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION-001", "지역을 찾을 수 없습니다."),
    REGION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "REGION-002", "서비스 지역이 아닌 주소입니다."),
    LOCATION_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "REGION-003", "위치 정보 이용 동의가 필요합니다."),

    MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "MEMBERSHIP-001", "멤버십 회원만 이용할 수 있습니다."),
    MEMBERSHIP_ALREADY_ACTIVE(HttpStatus.CONFLICT, "MEMBERSHIP-002", "이미 이용 중인 멤버십이 있습니다."),
    TRIAL_NOT_ELIGIBLE(HttpStatus.CONFLICT, "MEMBERSHIP-003", "무료체험은 한 번만 이용할 수 있습니다."),
    AUTOPAY_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "MEMBERSHIP-004", "자동 결제 동의가 필요합니다."),
    PAYMENT_METHOD_REQUIRED(HttpStatus.CONFLICT, "MEMBERSHIP-005", "등록된 결제 카드가 없습니다."),
    ACTIVE_MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBERSHIP-006", "이용 중인 멤버십이 없습니다."),
    NOT_CANCELABLE(HttpStatus.CONFLICT, "MEMBERSHIP-007", "자동갱신을 해지할 수 없는 멤버십입니다."),
    NOT_TERMINABLE(HttpStatus.CONFLICT, "MEMBERSHIP-008", "즉시 해지할 수 없는 멤버십입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
