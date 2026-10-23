package com.recipick.domain.order.dto;

import com.recipick.domain.order.entity.OrderItem;
import java.util.List;

// 주문명은 컬럼으로 저장하지 않고 주문 상품 스냅샷에서 만든다. PortOne 결제창·주문 목록·상세가 같은 이름을 보여야 해서 한곳에 둔다
public final class OrderNameFormatter {

    private OrderNameFormatter() {
    }

    // 주문 상품을 담은 순서(id 오름차순)로 받아야 첫 상품이 맞다
    public static String format(List<OrderItem> items) {
        String firstName = items.get(0).getProductName();
        return items.size() == 1 ? firstName : firstName + " 외 " + (items.size() - 1) + "건";
    }
}
