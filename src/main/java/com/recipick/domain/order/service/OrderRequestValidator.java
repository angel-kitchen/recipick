package com.recipick.domain.order.service;

import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.global.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

// 주문 미리보기와 주문 생성이 같은 입력을 같은 오류 코드로 거절해야 해서 한곳에 둔다.
// 요구사항의 오류 코드(ORDER-002, ORDER-006)로 답해야 해서 DTO의 jakarta validation으로 옮기지 않는다
public final class OrderRequestValidator {

    private static final Pattern LEGAL_DONG_CODE = Pattern.compile("\\d{10}");

    private OrderRequestValidator() {
    }

    public static void validateNoDuplicateItems(List<Long> cartItemIds) {
        if (new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER_ITEM);
        }
    }

    public static void validateLegalDongCode(String legalDongCode) {
        if (legalDongCode == null || !LEGAL_DONG_CODE.matcher(legalDongCode).matches()) {
            throw new BusinessException(OrderErrorCode.INVALID_DELIVERY_INFORMATION);
        }
    }
}
