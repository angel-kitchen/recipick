package com.recipick.domain.order.repository;

import com.recipick.domain.order.entity.Order;
import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatusGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    Page<Order> searchByMember(Long memberId, OrderStatusGroup statusGroup, Pageable pageable);

    Page<Order> searchByMart(Long martId, MartStatus martStatus, Pageable pageable);

    boolean existsInProgressByMemberId(Long memberId);
}
