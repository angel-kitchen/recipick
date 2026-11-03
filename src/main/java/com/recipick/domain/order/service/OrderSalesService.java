package com.recipick.domain.order.service;

import com.recipick.domain.order.entity.enums.MartStatus;
import com.recipick.domain.order.entity.enums.OrderStatus;
import com.recipick.domain.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSalesService {

    private final OrderItemRepository orderItemRepository;

    public List<Long> findBestSellingProductIds(Long martId, LocalDateTime from, int limit){
        return orderItemRepository.findBestSellingProductIds(
            martId, OrderStatus.PAID, MartStatus.REJECTED, from, PageRequest.of(0, limit));
    }
}
