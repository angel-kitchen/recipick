package com.recipick.infra.ai.dto;

import java.util.List;

public record AiExtractedMenu(String mention, List<Long> recipeIds, Integer servings) {
}
