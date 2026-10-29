package com.recipick.domain.recipe.dto.request;

import com.recipick.domain.recipe.entity.AiRequestType;
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

// 요청 유형·기간을 String으로 받아, 변환 실패가 바인딩 오류(COMMON-002)가 아니라 입력 검증(COMMON-001)으로 모이게 한다
public record AiRequestLogAdminSearchRequest(

    @Pattern(regexp = "AUTO_CART|RECOMMENDATION", message = "지원하지 않는 AI 요청 유형입니다.")
    String requestType,

    Boolean success,

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "조회 시작일은 yyyy-MM-dd 형식이어야 합니다.")
    String from,

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "조회 종료일은 yyyy-MM-dd 형식이어야 합니다.")
    String to,

    @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
    Integer page,

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    Integer size
) {

    public AiRequestLogAdminSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    // 형식은 맞지만 없는 날짜(2026-02-30)와 시작일이 종료일보다 늦은 기간을 함께 거른다
    @AssertTrue(message = "조회 기간이 올바르지 않습니다.")
    public boolean isDateRangeValid() {
        try {
            LocalDate fromDate = from == null ? null : LocalDate.parse(from);
            LocalDate toDate = to == null ? null : LocalDate.parse(to);
            return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public AiRequestType toRequestType() {
        return requestType == null ? null : AiRequestType.valueOf(requestType);
    }

    public LocalDateTime toFromAt() {
        return from == null ? null : LocalDate.parse(from).atStartOfDay();
    }

    // 종료일 하루 전체를 포함하도록 다음 날 00:00 미만으로 자른다
    public LocalDateTime toToExclusive() {
        return to == null ? null : LocalDate.parse(to).plusDays(1).atStartOfDay();
    }

    // 최신순 고정. 같은 시각에 쌓인 이력의 순서가 페이지마다 흔들리지 않게 id를 보조 정렬로 붙인다
    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
