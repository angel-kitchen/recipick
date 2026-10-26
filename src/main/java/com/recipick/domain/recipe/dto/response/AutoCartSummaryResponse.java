package com.recipick.domain.recipe.dto.response;

import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.entity.AutoCartStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AutoCartSummaryResponse(
        Long autoCartRequestId,
        AutoCartStatus status,
        String martName,
        String menuSummary,
        Long totalPrice,
        LocalDateTime createdAt
) {

    public static AutoCartSummaryResponse of(AutoCartRequest request, String martName) {
        return new AutoCartSummaryResponse(
            request.getId(),
            request.getStatus(),
            martName,
            summarize(request.getMenus()),
            request.getTotalPrice(),
            request.getCreatedAt()
        );
    }

    private static String summarize(List<AutoCartMenu> menus) {
        AutoCartMenu first = menus.get(0);
        AutoCartMenu second = menus.get(1);
        String summary = first.recipeName() + " " + first.servings() + "인분";
        if (menus.size() == 1) {
            return summary;
        }
        return summary + " 외 " + (menus.size() - 1) + "건";
    }
}
