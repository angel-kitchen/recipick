package com.recipick.domain.payment.dto.request;

import com.recipick.domain.payment.entity.enums.PaymentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// 관리자 결제 목록(1-6)의 쿼리 파라미터. status·from·to를 String으로 받는 이유는
// enum·날짜 변환 실패가 바인딩 단계(COMMON-002)가 아니라 Bean Validation(COMMON-001)으로 모이게 하기 위해서다
public record PaymentAdminSearchRequest(

    @Pattern(
        regexp = "IN_PROGRESS|PAID|FAILED|CANCEL_REQUESTED|CANCELED",
        message = "지원하지 않는 결제 상태입니다."
    )
    String status,

    @Min(value = 1, message = "회원 ID는 1 이상이어야 합니다.")
    Long memberId,

    @Pattern(
        regexp = "\\d{4}-\\d{2}-\\d{2}",
        message = "조회 시작일은 yyyy-MM-dd 형식이어야 합니다."
    )
    String from,

    @Pattern(
        regexp = "\\d{4}-\\d{2}-\\d{2}",
        message = "조회 종료일은 yyyy-MM-dd 형식이어야 합니다."
    )
    String to,

    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    Integer page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    Integer size,

    @Pattern(
        regexp = "createdAt,(?i:asc|desc)",
        message = "정렬은 createdAt,asc 또는 createdAt,desc만 사용할 수 있습니다."
    )
    String sort
) {

    public PaymentAdminSearchRequest {
        status = strip(status);
        from = strip(from);
        to = strip(to);
        sort = sort == null ? "createdAt,desc" : sort.strip();
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    // 형식은 맞지만 없는 날짜(2026-02-30)와 시작일이 종료일보다 늦은 기간을 함께 거른다
    @AssertTrue(message = "조회 기간이 올바르지 않습니다.")
    public boolean isDateRangeValid() {
        try {
            LocalDate fromDate = from == null ? null : LocalDate.parse(from);
            LocalDate toDate = to == null ? null : LocalDate.parse(to);

            return fromDate == null
                || toDate == null
                || !fromDate.isAfter(toDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public PaymentStatus toPaymentStatus() {
        return status == null ? null : PaymentStatus.valueOf(status);
    }

    // 시작일 00:00부터 포함한다
    public LocalDateTime toFromAt() {
        return from == null
            ? null
            : LocalDate.parse(from).atStartOfDay();
    }

    // 종료일 하루 전체를 포함하도록 다음 날 00:00 미만으로 자른다
    public LocalDateTime toToExclusive() {
        return to == null
            ? null
            : LocalDate.parse(to).plusDays(1).atStartOfDay();
    }

    // createdAt이 같은 결제의 순서가 페이지마다 흔들리지 않게 id를 같은 방향의 보조 정렬로 붙인다
    public Pageable toPageable() {
        String[] parts = sort.split(",", -1);
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);

        return PageRequest.of(
            page,
            size,
            Sort.by(
                new Sort.Order(direction, "createdAt"),
                new Sort.Order(direction, "id")
            )
        );
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    @Override
    public String toString() {
        return "PaymentAdminSearchRequest[REDACTED]";
    }
}
