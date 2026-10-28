package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.CategoryCreateRequest;
import com.recipick.domain.product.dto.CategoryIdResponse;
import com.recipick.domain.product.dto.CategoryResponse;
import com.recipick.domain.product.dto.CategoryUpdateRequest;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.ProductRepository;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recipick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private com.recipick.global.audit.AdminAuditRecorder auditRecorder;

    @InjectMocks
    private CategoryService categoryService;

    private Category category(Long id, String name){
        Category category = Category.builder().name(name).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Test
    @DisplayName("목록: 삭제되지 않은 카테고리를 응답으로 바꿔 돌려준다.")
    void getCategories(){
        given(categoryRepository.findAllByDeletedFalse())
            .willReturn(List.of(category(1L,"채소"), category(2L,"육류")));

        List<CategoryResponse> result = categoryService.getCategories();
    }

    @Test
    @DisplayName("등록: 성공하면 저장된 id를 돌려준다.")
    void createCategory(){
        given(categoryRepository.findByNameForUpdate("채소")).willReturn(Optional.empty());
        given(categoryRepository.save(any(Category.class))).willReturn(category(1L,"채소"));

        CategoryIdResponse result = categoryService.createCategory(new CategoryCreateRequest("채소"));

        assertThat(result.categoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록: 이름이 중복이면 CATEGORY_NAME_DUPLICATED")
    void createCategory_duplicated(){
        given(categoryRepository.findByNameForUpdate("채소")).willReturn(Optional.of(category(1L, "채소")));

        assertThatThrownBy(() -> categoryService.createCategory(new CategoryCreateRequest("채소")))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록 : 삭제된 같은 이름이 있으면 새로 만들지 않고 복구한다")
    void createCategory_restoreDeleted(){
        // given
        Category deleted = category(1L, "채소");
        deleted.delete();
        given(categoryRepository.findByNameForUpdate("채소")).willReturn(Optional.of(deleted));

        // when
        CategoryIdResponse result = categoryService.createCategory(
            new CategoryCreateRequest("채소"));

        // then
        assertThat(result.categoryId()).isEqualTo(1L);
        assertThat(deleted.isDeleted()).isFalse();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정 : 새 이름이면 이름이 바뀐다")
    void updateCategory(){
        Category category = category(1L, "채소");
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("신선 채소")).willReturn(false);

        categoryService.updateCategory(1L, new CategoryUpdateRequest("신선 채소"));

        assertThat(category.getName()).isEqualTo("신선 채소");
    }

    @Test
    @DisplayName("수정 : 같은 이름이면 중복 검사 없이 성공한다")
    void updateCategory_sameName(){
        Category category = category(1L,"채소");
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));

        CategoryIdResponse result = categoryService.updateCategory(1L, new CategoryUpdateRequest("채소"));

        assertThat(result.categoryId()).isEqualTo(1L);
        verify(categoryRepository, never()).existsByName(any());
    }

    @Test
    @DisplayName("수정: 다른 카테고리와 이름이 겹치면 CATEGORY_NAME_DUPLICATED")
    void updateCategory_duplicated(){
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category(1L, "채소")));
        given(categoryRepository.existsByName("육류")).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, new CategoryUpdateRequest("육류")))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("수정: 없는 id면 CATEGORY_NOT_FOUND")
    void updateCategory_notFound(){
        given(categoryRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(99L, new CategoryUpdateRequest("채소")))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 삭제된 카테고리면 CATEGORY_ALREADY_DELETED")
    void updateCategory_alreadyDeleted(){
        Category category = category(1L, "채소");
        category.delete();
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, new CategoryUpdateRequest("신선 채소")))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_ALREADY_DELETED);
    }

    @Test
    @DisplayName("삭제: 상품이 없으면 deleted가 true가 된다")
    void delete(){
        Category category = category(1L, "채소");
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));
        given(productRepository.existsByCategoryIdAndDeletedFalse(1L)).willReturn(false);

        categoryService.delete(1L);

        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제: 상품이 연결돼 있으면 CATEGORY_HAS_PRODUCTS")
    void delete_hasProducts(){
        Category category = category(1L, "채소");
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));
        given(productRepository.existsByCategoryIdAndDeletedFalse(1L)).willReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_HAS_PRODUCTS);
        assertThat(category.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("삭제: 이미 삭제됐으면 CATEGORY_ALREADY_DELETED")
    void delete_alreadyDeleted(){
        Category category = category(1L, "채소");
        category.delete();
        given(categoryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(category));

        assertThatThrownBy(()-> categoryService.delete(1L))
            .extracting(e-> ((BusinessException)e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_ALREADY_DELETED);
    }

    @Test
    @DisplayName("삭제 : 없는 id면 CATEGORY_NOT_FOUND")
    void delete_notFound(){
        given(categoryRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(()-> categoryService.delete(99L))
            .extracting(e -> ((BusinessException)e).getErrorCode())
            .isEqualTo(ProductErrorCode.CATEGORY_NOT_FOUND);
    }
}
