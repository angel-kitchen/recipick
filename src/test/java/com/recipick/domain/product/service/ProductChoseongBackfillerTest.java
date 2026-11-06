package com.recipick.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductChoseongBackfillerTest extends IntegrationTestSupport {

    private static final LocalDateTime OLD = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Autowired
    ProductChoseongBackfiller backfiller;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    MartRepository martRepository;

    @Autowired
    EntityManager em;

    Mart mart;
    Category category;

    @BeforeEach
    void setUp() {
        mart = martRepository.save(Mart.builder()
            .regionId(903L)
            .name("초성 채우기 마트")
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build());
        category = em.merge(Category.builder().name("초성채우기채소").build());
    }

    private Product saveProduct(String name) {
        return productRepository.save(Product.builder()
            .mart(mart)
            .category(category)
            .ingredientId(1L)
            .name(name)
            .price(1000L)
            .totalQuantity(10)
            .build());
    }

    // 이전 버전에서 저장돼 초성이 비어 있고 수정 시각이 오래된 행을 만든다
    private void makeLegacy(Product product) {
        em.flush();
        em.createNativeQuery("update product set name_choseong = null, updated_at = ?1 where id = ?2")
            .setParameter(1, OLD)
            .setParameter(2, product.getId())
            .executeUpdate();
        em.clear();
    }

    private String choseongOf(Long id) {
        return (String) em.createNativeQuery("select name_choseong from product where id = ?1")
            .setParameter(1, id)
            .getSingleResult();
    }

    private LocalDateTime updatedAtOf(Long id) {
        return ((Timestamp) em.createNativeQuery("select updated_at from product where id = ?1")
            .setParameter(1, id)
            .getSingleResult()).toLocalDateTime();
    }

    @Test
    @DisplayName("초성이 비어 있는 상품만 채우고 이미 채워진 상품은 그대로 둔다")
    void fillsOnlyEmptyRows() {
        // given
        Product legacy = saveProduct("애호박");
        Product filled = saveProduct("청양고추");
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        em.clear();

        // then
        assertThat(choseongOf(legacy.getId())).isEqualTo("ㅇㅎㅂ");
        assertThat(choseongOf(filled.getId())).isEqualTo("ㅊㅇㄱㅊ");
    }

    @Test
    @DisplayName("초성을 채워도 상품의 수정 시각은 바뀌지 않는다")
    void keepsUpdatedAt() {
        // given
        Product legacy = saveProduct("애호박");
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        em.clear();

        // then
        assertThat(updatedAtOf(legacy.getId())).isEqualTo(OLD);
    }

    @Test
    @DisplayName("여러 번 실행해도 결과가 같고, 이름이 없는 상품은 건너뛰어 반복이 끝난다")
    void idempotentAndSkipsNullName() {
        // given
        Product legacy = saveProduct("삼겹살");
        Product noName = saveProduct(null);
        makeLegacy(legacy);

        // when
        backfiller.run(null);
        backfiller.run(null);
        em.clear();

        // then
        assertThat(choseongOf(legacy.getId())).isEqualTo("ㅅㄱㅅ");
        assertThat(choseongOf(noName.getId())).isNull();
    }
}
