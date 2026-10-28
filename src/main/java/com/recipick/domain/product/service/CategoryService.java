package com.recipick.domain.product.service;

import java.util.Map;
import com.recipick.global.audit.AdminAuditAction;
import com.recipick.global.audit.AdminAuditCommand;
import com.recipick.global.audit.AdminAuditRecorder;
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
import com.recipick.global.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final AdminAuditRecorder auditRecorder;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Cacheable(cacheNames = CacheConfig.CATEGORIES)
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(){
        return categoryRepository.findAllByDeletedFalse().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryIdResponse createCategory(CategoryCreateRequest request) {
        Optional<Category> existing = categoryRepository.findByNameForUpdate(request.name());
        if (existing.isPresent()) {
            Category category = existing.get();
            if (!category.isDeleted()) {
                throw new BusinessException(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            category.restore();
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.CATEGORY_CREATE, category.getId(), null, Map.of()));
            return new CategoryIdResponse(category.getId());
        }
        Category category = Category.builder()
            .name(request.name())
            .build();
        Category saved = categoryRepository.save(category);
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.CATEGORY_CREATE, saved.getId(), null, Map.of()));
        return new CategoryIdResponse(saved.getId());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryIdResponse updateCategory(Long categoryId, CategoryUpdateRequest request){
        Category category = categoryRepository.findByIdForUpdate(categoryId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        if(category.isDeleted()){
            throw new BusinessException(ProductErrorCode.CATEGORY_ALREADY_DELETED);
        }
        if(!category.getName().equals(request.name())){
            if(categoryRepository.existsByName(request.name())){
                throw new BusinessException(ProductErrorCode.CATEGORY_NAME_DUPLICATED);
            }
            category.rename(request.name());
            auditRecorder.record(new AdminAuditCommand(AdminAuditAction.CATEGORY_UPDATE, category.getId(), null, Map.of()));
        }
        return new CategoryIdResponse(category.getId());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
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
        auditRecorder.record(new AdminAuditCommand(AdminAuditAction.CATEGORY_DELETE, category.getId(), null, Map.of()));
    }

}
