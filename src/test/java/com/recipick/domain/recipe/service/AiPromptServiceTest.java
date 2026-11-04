package com.recipick.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.recipick.domain.recipe.dto.ActiveAiPrompt;
import com.recipick.domain.recipe.dto.AiPromptActiveChange;
import com.recipick.domain.recipe.entity.AiPrompt;
import com.recipick.domain.recipe.entity.AiPromptType;
import com.recipick.domain.recipe.exception.AiErrorCode;
import com.recipick.domain.recipe.repository.AiPromptRepository;
import com.recipick.global.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiPromptServiceTest {

    private static final Long ADMIN_ID = 7L;

    @Mock
    private AiPromptRepository aiPromptRepository;

    @InjectMocks
    private AiPromptService aiPromptService;

    @Test
    @DisplayName("활성 버전이 없으면 코드 기본 지시문을 버전 없이 돌려준다")
    void getActive_usesDefaultWithoutActiveVersion() {
        // given
        given(aiPromptRepository.findByPromptTypeAndActiveTrue(AiPromptType.MENU)).willReturn(Optional.empty());

        // when
        ActiveAiPrompt active = aiPromptService.getActive(AiPromptType.MENU);

        // then
        assertThat(active.version()).isNull();
        assertThat(active.content()).isEqualTo(AiPromptType.MENU.defaultContent());
    }

    @Test
    @DisplayName("활성 버전이 있으면 그 버전의 지시문을 돌려준다")
    void getActive_usesActiveVersion() {
        // given
        AiPrompt prompt = prompt(10L, AiPromptType.MENU, 3, true);
        given(aiPromptRepository.findByPromptTypeAndActiveTrue(AiPromptType.MENU)).willReturn(Optional.of(prompt));

        // when
        ActiveAiPrompt active = aiPromptService.getActive(AiPromptType.MENU);

        // then
        assertThat(active.version()).isEqualTo(3);
        assertThat(active.content()).isEqualTo("지시문 3");
    }

    @Test
    @DisplayName("새 버전은 같은 종류의 가장 큰 버전 다음 번호로 비활성 상태로 저장한다")
    void create_savesNextInactiveVersion() {
        // given
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.CONDITION))
            .willReturn(List.of(
                prompt(1L, AiPromptType.CONDITION, 1, false), prompt(2L, AiPromptType.CONDITION, 2, true)));
        given(aiPromptRepository.saveAndFlush(any(AiPrompt.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPrompt created = aiPromptService.create(AiPromptType.CONDITION, "새 지시문", "예산 조건 추가", ADMIN_ID);

        // then
        assertThat(created.getVersion()).isEqualTo(3);
        assertThat(created.isActive()).isFalse();
        assertThat(created.getCreatedByAdminId()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("첫 버전은 1번으로 저장한다")
    void create_startsFromVersionOne() {
        // given
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.FRIDGE)).willReturn(List.of());
        given(aiPromptRepository.saveAndFlush(any(AiPrompt.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        AiPrompt created = aiPromptService.create(AiPromptType.FRIDGE, "새 지시문", null, ADMIN_ID);

        // then
        assertThat(created.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 버전 번호가 먼저 저장됐으면 충돌로 알린다")
    void create_failsWhenVersionTaken() {
        // given
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.MENU)).willReturn(List.of());
        given(aiPromptRepository.saveAndFlush(any(AiPrompt.class)))
            .willThrow(new DataIntegrityViolationException("uk_ai_prompt_type_version"));

        // when & then
        assertThatThrownBy(() -> aiPromptService.create(AiPromptType.MENU, "새 지시문", null, ADMIN_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(AiErrorCode.AI_PROMPT_VERSION_CONFLICT);
    }

    @Test
    @DisplayName("활성화하면 같은 종류의 다른 버전은 모두 비활성이 된다")
    void activate_keepsOnlyTargetActive() {
        // given
        AiPrompt first = prompt(1L, AiPromptType.MENU, 1, true);
        AiPrompt second = prompt(2L, AiPromptType.MENU, 2, false);
        given(aiPromptRepository.findById(2L)).willReturn(Optional.of(second));
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.MENU)).willReturn(List.of(first, second));

        // when
        AiPromptActiveChange change = aiPromptService.activate(2L);

        // then
        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isTrue();
        assertThat(change).isEqualTo(new AiPromptActiveChange(AiPromptType.MENU, 2L, 1, 2));
        assertThat(change.changed()).isTrue();
    }

    @Test
    @DisplayName("이미 활성인 버전을 다시 활성화하면 바뀐 것이 없다")
    void activate_reportsNoChangeWhenAlreadyActive() {
        // given
        AiPrompt active = prompt(1L, AiPromptType.MENU, 1, true);
        given(aiPromptRepository.findById(1L)).willReturn(Optional.of(active));
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.MENU)).willReturn(List.of(active));

        // when
        AiPromptActiveChange change = aiPromptService.activate(1L);

        // then
        assertThat(active.isActive()).isTrue();
        assertThat(change.changed()).isFalse();
    }

    @Test
    @DisplayName("없는 버전을 활성화하면 AI-002로 거절한다")
    void activate_failsWhenPromptMissing() {
        // given
        given(aiPromptRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> aiPromptService.activate(99L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(AiErrorCode.AI_PROMPT_NOT_FOUND);
    }

    @Test
    @DisplayName("되돌리면 활성 버전을 끄고 꺼진 버전을 알려 준다")
    void reset_deactivatesActiveVersion() {
        // given
        AiPrompt active = prompt(4L, AiPromptType.RECOMMEND, 2, true);
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.RECOMMEND)).willReturn(List.of(active));

        // when
        Optional<AiPromptActiveChange> change = aiPromptService.reset(AiPromptType.RECOMMEND);

        // then
        assertThat(active.isActive()).isFalse();
        assertThat(change).contains(new AiPromptActiveChange(AiPromptType.RECOMMEND, 4L, 2, null));
    }

    @Test
    @DisplayName("활성 버전이 없는데 되돌리면 바뀐 것이 없다")
    void reset_isEmptyWithoutActiveVersion() {
        // given
        given(aiPromptRepository.findAllByPromptTypeForUpdate(AiPromptType.RECOMMEND))
            .willReturn(List.of(prompt(4L, AiPromptType.RECOMMEND, 1, false)));

        // when
        Optional<AiPromptActiveChange> change = aiPromptService.reset(AiPromptType.RECOMMEND);

        // then
        assertThat(change).isEmpty();
    }

    private static AiPrompt prompt(Long id, AiPromptType type, int version, boolean active) {
        AiPrompt prompt = AiPrompt.builder()
            .promptType(type)
            .version(version)
            .content("지시문 " + version)
            .createdByAdminId(ADMIN_ID)
            .build();
        ReflectionTestUtils.setField(prompt, "id", id);
        if (active) {
            prompt.activate();
        }
        return prompt;
    }
}
