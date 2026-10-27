package com.recipick.domain.payment.repository;

import com.recipick.domain.payment.entity.Payment;
import com.recipick.domain.payment.repository.dto.PaymentAdminSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepositoryCustom {

    // 관리자 결제 목록. 선택 조건이 조합되므로 QueryDSL 구현체(PaymentRepositoryImpl)가 처리한다
    Page<Payment> searchForAdmin(PaymentAdminSearchCondition condition, Pageable pageable);
}
