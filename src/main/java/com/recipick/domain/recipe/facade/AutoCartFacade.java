package com.recipick.domain.recipe.facade;

import com.recipick.domain.member.exception.MemberErrorCode;
import com.recipick.domain.member.service.MemberService;
import com.recipick.domain.member.service.MembershipService;
import com.recipick.domain.order.dto.response.CartItemAddResponse;
import com.recipick.domain.order.exception.OrderErrorCode;
import com.recipick.domain.order.service.CartService;
import com.recipick.domain.product.dto.MartResponse;
import com.recipick.domain.product.dto.ProductSummary;
import com.recipick.domain.product.exception.ProductErrorCode;
import com.recipick.domain.product.service.MartService;
import com.recipick.domain.product.service.ProductIngredientService;
import com.recipick.domain.product.service.ProductService;
import com.recipick.domain.recipe.dto.request.AutoCartConfirmRequest;
import com.recipick.domain.recipe.dto.request.AutoCartPreviewRequest;
import com.recipick.domain.recipe.dto.response.AutoCartConfirmResponse;
import com.recipick.domain.recipe.dto.response.AutoCartPreviewResponse;
import com.recipick.domain.recipe.dto.response.AutoCartSummaryResponse;
import com.recipick.domain.recipe.entity.AutoCartMenu;
import com.recipick.domain.recipe.entity.AutoCartRequest;
import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.domain.recipe.service.AutoCartCalculator;
import com.recipick.domain.recipe.service.AutoCartLine;
import com.recipick.domain.recipe.service.AutoCartService;
import com.recipick.domain.recipe.service.RequiredIngredient;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.response.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ProductService productService;
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

    // AI를 부르기 전에 미리보기를 만들 수 없는 회원을 걸러, 쓰지 못할 결과에 AI 비용을 쓰지 않게 한다
    void validatePreviewable(Long memberId) {
        validateMembership(memberId);
        getMartId(memberId);
    }

    // 확정 직전 화면이 실제 확정 결과와 같도록, 확정 전 요청은 저장된 메뉴로 현재 가격·재고 기준 다시 계산한다
    @Transactional(readOnly = true)
    public AutoCartPreviewResponse getAutoCart(Long memberId, Long autoCartRequestId) {
        AutoCartRequest request = autoCartService.getRequest(memberId, autoCartRequestId);
        String martName = getMartName(request.getMartId());
        if (request.isConfirmed()) {
            return AutoCartPreviewResponse.ofSnapshot(request, martName, autoCartService.findItems(autoCartRequestId));
        }
        List<AutoCartLine> lines = calculate(memberId, request.getMartId(), request.getMenus(), Map.of());
        return AutoCartPreviewResponse.of(request, martName, lines);
    }

    @Transactional
    public AutoCartConfirmResponse confirm(Long memberId, Long autoCartRequestId, AutoCartConfirmRequest body) {
        // 탈퇴와 겹쳐도 탈퇴 뒤에 장바구니 항목이 남지 않도록 회원 행을 먼저 잠근다(MEMBER → AUTO_CART_REQUEST).
        // 연타·재전송으로 같은 요청이 동시에 들어와도 한 번만 합산되도록, 요청 행을 잠근 뒤 상태를 본다
        memberService.lockActiveMember(memberId);
        AutoCartRequest request = autoCartService.getRequestForUpdate(memberId, autoCartRequestId);
        request.validateConfirmable();
        validateMembership(memberId);
        Long martId = getMartId(memberId);
        if (!martId.equals(request.getMartId())) {
            throw new BusinessException(RecipeErrorCode.AUTO_CART_MART_CHANGED);
        }
        validateNoPreviousMartItems(memberId, martId);

        List<AutoCartLine> lines = calculate(memberId, martId, request.getMenus(), toProductSelections(body));
        List<AutoCartLine> purchasableLines = lines.stream().filter(AutoCartLine::purchasable).toList();
        if (purchasableLines.isEmpty()) {
            throw new BusinessException(RecipeErrorCode.NO_PURCHASABLE_INGREDIENTS);
        }
        List<AutoCartConfirmResponse.AddedItem> addedItems = purchasableLines.stream()
            .map(line -> addToCart(memberId, autoCartRequestId, line))
            .toList();
        long totalPrice = sumLinePrice(lines);
        boolean priceChanged = request.getTotalPrice() != totalPrice;
        autoCartService.confirm(request, lines, totalPrice, LocalDateTime.now(clock));

        return new AutoCartConfirmResponse(
            autoCartRequestId,
            request.getStatus(),
            addedItems,
            lines.stream()
                .filter(line -> !line.purchasable())
                .map(AutoCartConfirmResponse.ExcludedItem::from)
                .toList(),
            totalPrice,
            priceChanged
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AutoCartSummaryResponse> findAutoCarts(Long memberId, Pageable pageable)
    {
        Page<AutoCartRequest> requests = autoCartService.findRequests(memberId, pageable);
        Map<Long, String> martNames = requests.stream()
            .map(AutoCartRequest::getMartId)
            .distinct()
            .collect(Collectors.toMap(Function.identity(), this::getMartName));
        return PageResponse.from(requests.map(request ->
            AutoCartSummaryResponse.of(request, martNames.get(request.getMartId()))));
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

    private AutoCartConfirmResponse.AddedItem addToCart(Long memberId, Long autoCartRequestId, AutoCartLine line) {
        ProductSummary product = line.selected().product();
        CartItemAddResponse cartItem = cartService.addAutoCartItem(
            memberId, product.productId(), line.purchaseQuantity(), product.totalQuantity(), autoCartRequestId);
        return new AutoCartConfirmResponse.AddedItem(
            product.productId(), product.name(), line.purchaseQuantity(), cartItem.quantity(), line.linePrice());
    }

    // 직접 담기와 같은 규칙: 이전 마트 상품이 남아 있으면 한 장바구니에 두 마트 상품이 섞이므로 먼저 비우게 한다
    private void validateNoPreviousMartItems(Long memberId, Long martId) {
        boolean hasPreviousMartItems = productService.findProducts(cartService.findProductIds(memberId)).stream()
            .anyMatch(product -> !product.martId().equals(martId));
        if (hasPreviousMartItems) {
            throw new BusinessException(OrderErrorCode.CART_HAS_PREVIOUS_MART_ITEMS);
        }
    }

    // 같은 재료에 상품을 두 번 고르면 어느 쪽을 담을지 정할 수 없으므로 잘못된 선택으로 본다
    private Map<Long, Long> toProductSelections(AutoCartConfirmRequest body) {
        if (body == null || body.productSelections() == null) {
            return Map.of();
        }
        return body.productSelections().stream()
            .collect(Collectors.toMap(
                AutoCartConfirmRequest.ProductSelection::ingredientId,
                AutoCartConfirmRequest.ProductSelection::productId,
                (first, second) -> {
                    throw new BusinessException(RecipeErrorCode.INVALID_PRODUCT_SELECTION);
                }
            ));
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
