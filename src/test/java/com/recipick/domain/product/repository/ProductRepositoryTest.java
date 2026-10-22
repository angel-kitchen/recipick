package com.recipick.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.entity.ProductStatus;
import com.recipick.domain.product.repository.dto.AdminProductSearchCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Transactional
class ProductRepositoryTest extends IntegrationTestSupport {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    MartRepository martRepository;

    @Autowired
    EntityManager em;

    Mart martA;
    Mart martB;
    Category vegetable;
    Category meat;

    @BeforeEach
    void setUp(){
        martA = martRepository.save(mart(901L, "테스트 마트A"));
        martB = martRepository.save(mart(902L, "테스트 마트B"));
        vegetable = em.merge(Category.builder().name("테스트채소").build());
        meat = em.merge(Category.builder().name("테스트고기").build());

        saveProduct(martA, vegetable, "애호박", 1L, false);
        saveProduct(martA, vegetable, "청양고추", 2L, false);
        saveProduct(martA, meat, "삼겹살", 3L, false);
        saveProduct(martA, vegetable, "삭제된 양파", 4L, true);
        saveProduct(martB, vegetable, "다른마트 애호박", 1L, false);
        em.flush();
        em.clear();
    }

    private Mart mart(Long regionId, String name) {
        return Mart.builder()
            .regionId(regionId)
            .name(name)
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build();
    }

    private void saveProduct(Mart mart, Category category, String name, Long ingredientId, boolean deleted) {
        productRepository.save(Product.builder()
            .mart(mart)
            .category(category)
            .ingredientId(ingredientId)
            .name(name)
            .price(1000L)
            .totalQuantity(10)
            .deleted(deleted)
            .build());
    }

    private AdminProductSearchCondition condition(Long martId, String keyword, Long categoryId,
                                                  Long ingredientId, ProductStatus status) {
        return new AdminProductSearchCondition(martId, keyword, categoryId, ingredientId, status);
    }

    @Test
    @DisplayName("삭제된 상품은 목록에 나오지 않는다")
    void excludesDeleted() {
        // given
        AdminProductSearchCondition condition = condition(martA.getId(), null, null, null, null);

        // when
        Page<Product> result = productRepository.searchAdminProducts(condition, PageRequest.of(0, 20));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(Product::getName)
            .doesNotContain("삭제된 양파");
    }

    @Test
    @DisplayName("마트로 필터링하면 해당 마트 상품만 나온다")
    void filterByMart() {
        // given
        AdminProductSearchCondition condition = condition(martB.getId(), null, null, null, null);

        // when
        Page<Product> result = productRepository.searchAdminProducts(condition, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).extracting(Product::getName)
            .containsExactly("다른마트 애호박");
    }

    @Test
    @DisplayName("키워드는 상품명 일부로 검색된다")
    void filterByKeyword() {
        // given
        AdminProductSearchCondition condition = condition(martA.getId(), "호박", null, null, null);

        // when
        Page<Product> result = productRepository.searchAdminProducts(condition, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).extracting(Product::getName).containsExactly("애호박");
    }

    @Test
    @DisplayName("카테고리와 재료로 필터링한다")
    void filterByCategoryAndIngredient() {
        // given
        AdminProductSearchCondition byCategory = condition(martA.getId(), null, meat.getId(), null, null);
        AdminProductSearchCondition byIngredient = condition(martA.getId(), null, null, 2L, null);

        // when
        Page<Product> categoryResult = productRepository.searchAdminProducts(byCategory, PageRequest.of(0, 20));
        Page<Product> ingredientResult = productRepository.searchAdminProducts(byIngredient, PageRequest.of(0, 20));

        // then
        assertThat(categoryResult.getContent()).extracting(Product::getName).containsExactly("삼겹살");
        assertThat(ingredientResult.getContent()).extracting(Product::getName).containsExactly("청양고추");
    }

    @Test
    @DisplayName("판매 상태로 필터링한다")
    void filterByStatus() {
        // given
        AdminProductSearchCondition condition =
            condition(martA.getId(), null, null, null, ProductStatus.SELLING);

        // when
        Page<Product> result = productRepository.searchAdminProducts(condition, PageRequest.of(0, 20));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("최신 등록순으로 정렬되고 페이징된다")
    void sortAndPaging() {
        // given
        AdminProductSearchCondition condition = condition(martA.getId(), null, null, null, null);

        // when
        Page<Product> firstPage = productRepository.searchAdminProducts(condition, PageRequest.of(0, 2));
        Page<Product> secondPage = productRepository.searchAdminProducts(condition, PageRequest.of(1, 2));

        // then
        assertThat(firstPage.getContent()).extracting(Product::getName)
            .containsExactly("삼겹살", "청양고추");
        assertThat(secondPage.getContent()).extracting(Product::getName)
            .containsExactly("애호박");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        // when
        Page<Product> result = productRepository.searchAdminProducts(condition, PageRequest.of(0, 20));

        // then
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("중복 검사 : 이름이 같고 둘 다 브랜드가 없으면 중복이다")
    void existsDuplicate_bothNoBrand() {
        // given
        Long martId = martA.getId();

        // when
        boolean exists = productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(martId, "애호박", null);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("중복 검사 : 한쪽만 브랜드가 있으면 다른 상품이다")
    void existsDuplicate_oneSideBrand() {
        // given
        Long martId = martA.getId();

        // when
        boolean exists = productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(martId, "애호박", "국산농장");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("중복 검사 : 삭제된 상품과 같은 이름은 중복이 아니다")
    void existsDuplicate_ignoresDeleted() {
        // given
        Long martId = martA.getId();

        // when
        boolean exists = productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(martId, "삭제된 양파", null);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("중복 검사 : 다른 마트에 있는 같은 이름은 중복이 아니다")
    void existsDuplicate_otherMart() {
        // given
        Long martId = martB.getId();

        // when
        boolean exists = productRepository.existsByMartIdAndNameAndBrandAndDeletedFalse(martId, "청양고추", null);

        // then
        assertThat(exists).isFalse();
    }
}
