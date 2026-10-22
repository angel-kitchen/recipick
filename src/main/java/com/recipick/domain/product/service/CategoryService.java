package com.recipick.domain.product.service;

import com.recipick.domain.product.dto.CategoryCreateRequest;
import com.recipick.domain.product.dto.CategoryIdResponse;
import com.recipick.domain.product.dto.CategoryResponse;
import com.recipick.domain.product.dto.CategoryUpdateRequest;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(){
        return categoryRepository.findAllByDeletedFalse().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @Transactional
    public CategoryIdResponse createCategory(CategoryCreateRequest request) {
        Optional<Category> existing = categoryRepository.findByNameForUpdate(request.name());
        if (existing.isPresent()) {
            Category category = existing.get();
            if (!category.isDeleted()) {
                throw new BusinessException(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            category.restore();
            return new CategoryIdResponse(category.getId());
        }
        Category category = Category.builder()
            .name(request.name())
            .build();
        return new CategoryIdResponse(categoryRepository.save(category).getId());
    }

    @Transactional
    public CategoryIdResponse updateCategory(Long categoryId, CategoryUpdateRequest request){
        Category category = findCategory(categoryId);
        if(category.isDeleted()){
            throw new BusinessException(ProductErrorCode.CATEGORY_ALREADY_DELETED);
        }
        if(!category.getName().equals(request.name())){
            if(categoryRepository.existsByName(request.name())){
                throw new BusinessException(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            category.rename(request.name());
        }
        return new CategoryIdResponse(category.getId());
    }

    @Transactional
    public void delete(Long categoryId){
        Category category = categoryRepository.findByIdForUpdate(categoryId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        if(category.isDeleted()){
            throw new BusinessException(ProductErrorCode.CATEGORY_ALREADY_DELETED);
        }
        if(productRepository.existsByCategoryIdAndDeletedFalse(categoryId)){
            throw new BusinessException(ProductErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        category.delete();
    }

    private Category findCategory(Long categoryId){
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }
}
