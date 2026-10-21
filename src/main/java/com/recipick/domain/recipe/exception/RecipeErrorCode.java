package com.recipick.domain.recipe.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecipeErrorCode implements ErrorCode {

    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE-001", "레시피를 찾을 수 없습니다."),
    RECIPE_INACTIVE(HttpStatus.CONFLICT, "RECIPE-002", "비활성화된 레시피입니다."),
    RECIPE_NOT_AUTO_CART_ENABLED(HttpStatus.CONFLICT, "RECIPE-003", "자동 장바구니를 지원하지 않는 레시피입니다."),
    AUTO_CART_ENABLE_CONDITION_NOT_MET(HttpStatus.CONFLICT, "RECIPE-004", "모든 필수 재료의 필요량·단위·상품 연결이 완료되어야 자동 장보기 레시피로 지정할 수 있습니다."),
    INVALID_YOUTUBE_URL(HttpStatus.BAD_REQUEST, "RECIPE-005", "올바른 유튜브 영상 주소가 아닙니다."),
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE-006", "재료를 찾을 수 없습니다."),
    DUPLICATE_INGREDIENT_NAME(HttpStatus.CONFLICT, "RECIPE-007", "이미 등록된 재료 이름입니다."),
    DUPLICATE_RECIPE_INGREDIENT(HttpStatus.CONFLICT, "RECIPE-008", "레시피에 이미 등록된 재료입니다."),
    INVALID_INGREDIENT_AMOUNT(HttpStatus.BAD_REQUEST, "RECIPE-009", "재료 필요량은 0보다 커야 합니다."),
    AUTO_CART_EMPTY_MENU(HttpStatus.BAD_REQUEST, "RECIPE-010", "메뉴를 하나 이상 선택해 주세요."),
    DUPLICATE_MENU_IN_REQUEST(HttpStatus.BAD_REQUEST, "RECIPE-011", "같은 메뉴가 중복으로 선택되었습니다."),
    INVALID_SERVINGS(HttpStatus.BAD_REQUEST, "RECIPE-012", "인분 수가 허용 범위를 벗어났습니다."),
    OPTIONAL_INGREDIENT_NOT_IN_RECIPE(HttpStatus.BAD_REQUEST, "RECIPE-013", "선택한 재료가 해당 레시피의 선택 재료가 아닙니다."),
    AUTO_CART_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE-014", "자동 구성 요청을 찾을 수 없습니다."),
    AUTO_CART_INVALID_STATUS(HttpStatus.CONFLICT, "RECIPE-015", "확정할 수 없는 상태의 자동 구성 요청입니다."),
    NO_PURCHASABLE_INGREDIENTS(HttpStatus.CONFLICT, "RECIPE-016", "재계산 결과 장바구니에 담을 수 있는 상품이 없습니다."),
    DUPLICATE_BOOKMARK(HttpStatus.CONFLICT, "RECIPE-017", "이미 찜한 레시피입니다."),
    INGREDIENT_IN_USE(HttpStatus.CONFLICT, "RECIPE-018", "레시피나 상품에 연결된 재료는 단위를 바꿀 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
