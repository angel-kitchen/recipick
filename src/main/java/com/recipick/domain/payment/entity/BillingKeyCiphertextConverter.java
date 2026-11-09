package com.recipick.domain.payment.entity;

import com.recipick.domain.payment.service.BillingKeyCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

// BillingKey.portoneBillingKey의 JPA 컨버터. 쓰기는 암호화, 읽기는 복호화라 호출부는 계속 원문 getter를 쓴다.
// Spring Boot가 Hibernate의 BeanContainer를 Spring으로 연결하므로 생성자 주입이 동작한다(통합 테스트가 확인).
// 더티 체크는 변환 전 도메인 값(원문)으로 하므로 저장마다 암호문이 달라도 불필요한 UPDATE는 생기지 않는다.
// NULL은 그대로 둔다 — 백필 전 행(암호문 NULL)을 읽을 때 복호화 예외 대신 null 원문이 되게
@Converter
@Component
public class BillingKeyCiphertextConverter implements AttributeConverter<String, String> {

    private final BillingKeyCipher cipher;

    public BillingKeyCiphertextConverter(BillingKeyCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return plaintext == null ? null : cipher.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.decrypt(stored);
    }
}
