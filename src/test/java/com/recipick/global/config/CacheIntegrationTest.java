package com.recipick.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.dto.CategoryCreateRequest;
import com.recipick.domain.product.dto.CategoryResponse;
import com.recipick.domain.product.dto.MartCreateRequest;
import com.recipick.domain.product.dto.ProductSearchRequest;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.domain.product.service.CategoryService;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(properties = "recipick.cache.enabled=true")
class CacheIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private MartService martService;
    @Autowired
    private ProductService productService;

    @MockitoSpyBean
    private CategoryRepository categoryRepository;
    @MockitoSpyBean
    private MartRepository martRepository;
    @MockitoSpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        clearInvocations(categoryRepository, martRepository, productRepository);
    }

    @Test
    @DisplayName("카테고리 목록은 두 번째 조회부터 DB를 거치지 않는다")
    void categories_areCached() {
        categoryService.getCategories();
        categoryService.getCategories();

        verify(categoryRepository, times(1)).findAllByDeletedFalse();
    }

    @Test
    @DisplayName("카테고리를 만들면 캐시가 비워져 다음 조회에 바로 반영된다")
    void createCategory_evictsCache() {
        int before = categoryService.getCategories().size();

        categoryService.createCategory(new CategoryCreateRequest("캐시검증카테고리"));

        assertThat(categoryService.getCategories()).hasSize(before + 1)
            .extracting(CategoryResponse::name).contains("캐시검증카테고리");
    }

    @Test
    @DisplayName("같은 이름으로 카테고리를 두 번 만들면 두 번째는 중복 오류가 난다(생성 결과가 캐시되지 않는다)")
    void createCategory_isNotCached() {
        categoryService.createCategory(new CategoryCreateRequest("중복검증카테고리"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> categoryService.createCategory(new CategoryCreateRequest("중복검증카테고리")))
            .isInstanceOf(com.recipick.global.exception.BusinessException.class);
    }

    @Test
    @DisplayName("영업 중 마트 목록은 캐시되고 마트를 만들면 비워진다")
    void openMarts_areCachedAndEvictedOnCreate() {
        martService.findOpenMarts();
        martService.findOpenMarts();
        verify(martRepository, times(1)).findAllByStatusOrderByIdAsc(any());

        martService.createMart(new MartCreateRequest(
            987654L, "캐시검증마트", new BigDecimal("37.367000"), new BigDecimal("127.108000"), 2500));
        martService.findOpenMarts();

        verify(martRepository, times(2)).findAllByStatusOrderByIdAsc(any());
    }

    @Test
    @DisplayName("같은 조건의 상품 검색은 두 번째부터 DB를 거치지 않고, 조건이 다르면 따로 저장한다")
    void productSearch_isCachedPerCondition() {
        ProductSearchRequest request = new ProductSearchRequest("대파", null, null, null);
        PageRequest page = PageRequest.of(0, 20, Sort.by("price"));

        var first = productService.searchProducts(1L, request, page);
        var second = productService.searchProducts(1L, request, page);
        assertThat(second).isEqualTo(first);
        verify(productRepository, times(1)).searchMemberProducts(any(), any());

        productService.searchProducts(1L, new ProductSearchRequest("양파", null, null, null), page);
        verify(productRepository, times(2)).searchMemberProducts(any(), any());
    }
}
