package com.recipick.domain.payment.entity.enums;

// 서버가 발급한 일회성 카드 등록 요청(issueId)의 상태
public enum BillingKeyIssueRequestStatus {
    REQUESTED,             // 발급됨. 유효기간 안에 한 키의 등록에만 쓸 수 있다
    USED                   // 한 빌링키 등록에 쓰임. 그 키에만 묶인다
}
