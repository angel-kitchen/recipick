package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import com.recipick.domain.recipe.entity.Recipe;
import com.recipick.domain.recipe.entity.RecipeBookmarkId;
import com.recipick.domain.recipe.entity.RecipeStatus;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.repository.RecipeBookmarkRepository;
import com.recipick.domain.recipe.repository.RecipeRepository;
import com.recipick.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class RecipeBookmarkConcurrencyTest extends IntegrationTestSupport {

    private static final Long MEMBER_ID = 9001L;

    @Autowired
    RecipeBookmarkService recipeBookmarkService;
    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    RecipeBookmarkRepository recipeBookmarkRepository;

    private Long recipeId;

    @AfterEach
    void cleanUp() {
        if (recipeId != null) {
            recipeBookmarkRepository.deleteById(new RecipeBookmarkId(MEMBER_ID, recipeId));
            recipeRepository.deleteById(recipeId);
        }
    }

    @Test
    @DisplayName("같은 회원이 같은 레시피를 10건 동시에 찜하면 1건만 저장되고 나머지는 DUPLICATE_BOOKMARK다")
    void addBookmark_concurrent() throws Exception {
        // given
        Recipe recipe = recipeRepository.save(Recipe.builder()
            .name("동시성 테스트 된장찌개")
            .baseServings(2)
            .steps(List.of())
            .build());
        recipeId = recipe.getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();

        // when
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    recipeBookmarkService.addBookmark(MEMBER_ID, recipeId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == RecipeErrorCode.DUPLICATE_BOOKMARK) {
                        duplicate.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // then
        assertThat(success.get()).isEqualTo(1);
        assertThat(duplicate.get()).isEqualTo(threads - 1);
        assertThat(recipeBookmarkRepository
            .findByIdMemberIdAndRecipeStatus(MEMBER_ID, RecipeStatus.ACTIVE, PageRequest.of(0, 20))
            .getTotalElements()).isEqualTo(1);
    }
}
