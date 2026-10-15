package com.recipick.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "mart_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MartProduct {

    @EmbeddedId
    private MartProductId id;

    @MapsId("martId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mart_id")
    private Mart mart;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Long price;

    // TODO: 값 목록 확정 후 enum으로 교체
    @Column(length = 20)
    private String status;

    private int totalQuantity;

    private int reservedQuantity;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    private MartProduct(Mart mart, Product product, Long price, String status, int totalQuantity) {
        this.id = new MartProductId(mart.getId(), product.getId());
        this.mart = mart;
        this.product = product;
        this.price = price;
        this.status = status;
        this.totalQuantity = totalQuantity;
    }
}
