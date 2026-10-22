package com.recipick.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.StockAdjustment;
import com.recipick.domain.product.entity.StockAdjustmentType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;


@Transactional
class StockAdjustmentRepositoryTest extends IntegrationTestSupport {

    @Autowired
    StockAdjustmentRepository stockAdjustmentRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    MartRepository martRepository;

    @Autowired
    EntityManager em;

    Product product;
    Product otherProduct;

    @BeforeEach
    void setUp() {
        Mart mart = martRepository.save(Mart.builder()
            .regionId(903L)
            .name("이력 테스트 마트")
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build());
        Category category = em.merge(Category.builder().name("이력테스트채소").build());
        product = productRepository.save(newProduct(mart, category, "애호박"));
        otherProduct = productRepository.save(newProduct(mart, category, "청양고추"));

        saveAdjustment(product, StockAdjustmentType.INCREASE, 30, 50, "입고");
        saveAdjustment(product, StockAdjustmentType.DECREASE, 5, 80, "폐기");
        saveAdjustment(product, StockAdjustmentType.INCREASE, 10, 75, "정정");
        saveAdjustment(otherProduct, StockAdjustmentType.INCREASE, 7, 0, "다른 상품");
        em.flush();
        em.clear();
    }

    private Product newProduct(Mart mart, Category category, String name) {
        return Product.builder()
            .mart(mart)
            .category(category)
            .name(name)
            .price(1000L)
            .totalQuantity(10)
            .build();
    }

    private void saveAdjustment(Product target, StockAdjustmentType type,
                                int quantity, int beforeQuantity, String reason) {
        stockAdjustmentRepository.save(StockAdjustment.builder()
            .product(target)
            .type(type)
            .quantity(quantity)
            .beforeQuantity(beforeQuantity)
            .reason(reason)
            .adminId(3L)
            .build());
    }

    @Test
    @DisplayName("해당 상품의 이력만 최신순으로 조회된다")
    void findByProductId_latestFirst() {
        // given
        Long productId = product.getId();

        // when
        Page<StockAdjustment> result = stockAdjustmentRepository
            .findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(0, 20));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(StockAdjustment::getReason)
            .containsExactly("정정", "폐기", "입고");
        assertThat(result.getContent()).extracting(StockAdjustment::getCreatedAt)
            .doesNotContainNull();
    }

    @Test
    @DisplayName("페이지 크기에 맞게 나뉘어 조회된다")
    void findByProductId_paging() {
        // given
        Long productId = product.getId();

        // when
        Page<StockAdjustment> firstPage = stockAdjustmentRepository
            .findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(0, 2));
        Page<StockAdjustment> secondPage = stockAdjustmentRepository
            .findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(1, 2));

        // then
        assertThat(firstPage.getContent()).extracting(StockAdjustment::getReason)
            .containsExactly("정정", "폐기");
        assertThat(secondPage.getContent()).extracting(StockAdjustment::getReason)
            .containsExactly("입고");
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("이력이 없는 상품은 빈 페이지가 나온다")
    void findByProductId_empty() {
        // given
        Long productId = productRepository.save(
            newProduct(product.getMart(), product.getCategory(), "이력 없는 상품")).getId();

        // when
        Page<StockAdjustment> result = stockAdjustmentRepository
            .findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
