package com.recipick.infra.ai.dto;

public record AiRecipeOption(Long id, String name, String thumbnailUrl) {

    public AiRecipeOption(Long id, String name) {
        this(id, name, null);
    }
}
