package com.recipick.domain.payment.dto.request;

import com.recipick.domain.payment.entity.enums.MembershipPaymentAttemptStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

// 멤버십 결제 시도 운영 목록의 쿼리 파라미터. 상태를 String으로 받는 이유는 enum 변환 실패를 Bean Validation(COMMON-001)으로
// 모으기 위해서다. 정렬은 시도 ID 최신순 고정
public record MembershipPaymentAttemptSearchRequest(

    @Pattern(
        regexp = "READY|DISPATCHING|UNKNOWN|MANUAL_REVIEW|PAID|FAILED|CANCELED",
        message = "지원하지 않는 결제 시도 상태입니다."
    )
    String status,

    @Min(value = 1, message = "이용권 ID는 1 이상이어야 합니다.")
    Long membershipId,

    Boolean opsReviewOnly,

    Boolean blockedOnly,

    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    Integer page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    Integer size
) {

    public MembershipPaymentAttemptSearchRequest {
        status = status == null ? null : status.strip();
        opsReviewOnly = Boolean.TRUE.equals(opsReviewOnly);
        blockedOnly = Boolean.TRUE.equals(blockedOnly);
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    public Set<MembershipPaymentAttemptStatus> toStatuses() {
        return status == null
            ? EnumSet.allOf(MembershipPaymentAttemptStatus.class)
            : EnumSet.of(MembershipPaymentAttemptStatus.valueOf(status));
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }
}
