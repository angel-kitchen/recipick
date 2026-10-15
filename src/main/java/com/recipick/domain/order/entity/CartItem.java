package com.recipick.domain.order.entity;

import com.recipick.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_item_member_id_product_id",
                columnNames = {"member_id", "product_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long productId;

    private int quantity;

    @Builder
    private CartItem(Long memberId, Long productId, int quantity) {
        this.memberId = memberId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
