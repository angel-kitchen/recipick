package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.dto.response.RecipeBookmarkCreateResponse;
import com.recipick.domain.recipe.dto.response.RecipeBookmarkResponse;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmark;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.recipick.domain.recipe.entity.QRecipeBookmark.recipeBookmark;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeBookmarkService {

    private final RecipeRepository recipeRepository;
    private final RecipeBookmarkRepository recipeBookmarkRepository;

    @Transactional
    public RecipeBookmarkCreateResponse addBookmark(Long memberId, Long recipeId) {
        Recipe recipe = recipeRepository.findByIdAndStatus(recipeId, RecipeStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
        if (recipeBookmarkRepository.existsById(new RecipeBookmarkId(memberId, recipeId))) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_BOOKMARK);
        }

        RecipeBookmark bookmark = RecipeBookmark.builder()
            .memberId(memberId)
            .recipe(recipe)
            .build();
        try {
            return RecipeBookmarkCreateResponse.from(recipeBookmarkRepository.saveAndFlush(bookmark));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RecipeErrorCode.DUPLICATE_BOOKMARK, e);
        }
    }

    @Transactional
    public void removeBookmark(Long memberId, Long recipeId) {
        recipeBookmarkRepository.deleteById(new RecipeBookmarkId(memberId, recipeId));
    }

    public PageResponse<RecipeBookmarkResponse> findMyBookmarks(Long memberId, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return PageResponse.from(recipeBookmarkRepository
            .findByIdMemberIdAndRecipeStatus(memberId, RecipeStatus.ACTIVE, latestFirst)
            .map(RecipeBookmarkResponse::from));
    }
}
