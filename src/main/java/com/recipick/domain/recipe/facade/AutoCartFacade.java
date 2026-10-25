package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.service.AutoCartCalculator;
import com.recipick.domain.recipe.service.AutoCartLine;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.domain.recipe.service.RequiredIngredient;
import com.recipick.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AutoCartFacade {

    private final AutoCartService autoCartService;
    private final MemberService memberService;
    private final MembershipService membershipService;
    private final MartService martService;
    private final ProductIngredientService productIngredientService;
    private final CartService cartService;
    private final Clock clock;

    @Transactional
    public AutoCartPreviewResponse preview(Long memberId, AutoCartPreviewRequest request) {
        validateMembership(memberId);
        Long martId = getMartId(memberId);
        List<AutoCartMenu> menus = autoCartService.resolveMenus(request.menus());
        List<AutoCartLine> lines = calculate(memberId, martId, menus, Map.of());
        AutoCartRequest saved = autoCartService.savePreview(memberId, martId, menus, sumLinePrice(lines));
        return AutoCartPreviewResponse.of(saved, getMartName(martId), lines);
    }

    // 확정 직전 화면이 실제 확정 결과와 같도록, 확정 전 요청은 저장된 메뉴로 현재 가격·재고 기준 다시 계산한다
    @Transactional(readOnly = true)
    public AutoCartPreviewResponse getAutoCart(Long memberId, Long autoCartRequestId) {
        AutoCartRequest request = autoCartService.getRequest(memberId, autoCartRequestId);
        List<AutoCartLine> lines = calculate(memberId, request.getMartId(), request.getMenus(), Map.of());
        return AutoCartPreviewResponse.of(request, getMartName(request.getMartId()), lines);
    }

    private List<AutoCartLine> calculate(
        Long memberId, Long martId, List<AutoCartMenu> menus, Map<Long, Long> productSelections) {
        List<RequiredIngredient> ingredients = autoCartService.sumIngredients(menus);
        Map<Long, List<ProductSummary>> candidates = productIngredientService.findCandidates(martId,
            ingredients.stream().map(RequiredIngredient::ingredientId).toList());
        Map<Long, Integer> cartQuantities = cartService.findQuantities(memberId, candidates.values().stream()
            .flatMap(List::stream)
            .map(ProductSummary::productId)
            .toList());
        return AutoCartCalculator.calculate(ingredients, candidates, cartQuantities, productSelections);
    }

    private void validateMembership(Long memberId) {
        if (!membershipService.getSummary(memberId, LocalDateTime.now(clock)).active()) {
            throw new BusinessException(MemberErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    private Long getMartId(Long memberId) {
        return memberService.getMartId(memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.MART_NOT_SELECTED));
    }

    private String getMartName(Long martId) {
        return martService.findMart(martId)
            .map(MartResponse::name)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.MART_NOT_FOUND));
    }

    private long sumLinePrice(List<AutoCartLine> lines) {
        return lines.stream().mapToLong(AutoCartLine::linePrice).sum();
    }
}
