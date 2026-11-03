package com.recipick.domain.order.service;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSalesService {

    private final OrderItemRepository orderItemRepository;

    public Page<Long> findBestSellingProductIds(Long martId, LocalDateTime from, Pageable pageable){
        return orderItemRepository.findBestSellingProductIds(
            martId, OrderStatus.PAID, MartStatus.REJECTED, from, pageable);
    }
}
