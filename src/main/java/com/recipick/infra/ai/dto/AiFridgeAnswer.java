package com.recipick.infra.ai.dto;

import java.util.List;

// recipeIds는 AI가 고른 레시피라, 레시피 찾기 Tool이 돌려준 레시피인지 domain에서 다시 확인한다
public record AiFridgeAnswer(String answer, List<Long> recipeIds) {
}
