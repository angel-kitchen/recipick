package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.OrderStatusGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    Page<Order> searchByMember(Long memberId, OrderStatusGroup statusGroup, Pageable pageable);
}
