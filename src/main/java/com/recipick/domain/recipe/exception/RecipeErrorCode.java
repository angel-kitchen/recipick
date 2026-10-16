package com.recipick.domain.recipe.exception;

import com.recipick.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecipeErrorCode implements ErrorCode {

    // ==== 레시피 ====
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "레시피를 찾을 수 없습니다."),
    RECIPE_INACTIVE(HttpStatus.CONFLICT, "비활성화된 레시피입니다."),
    RECIPE_NOT_AUTO_CART_ENABLED(HttpStatus.CONFLICT, "자동 장바구니를 지원하지 않는 레시피입니다."),
    AUTO_CART_ENABLE_CONDITION_NOT_MET(HttpStatus.CONFLICT, "모든 필수 재료의 필요량·단위·상품 연결이 완료되어야 자동 장보기 레시피로 지정할 수 있습니다."),
    INVALID_YOUTUBE_URL(HttpStatus.BAD_REQUEST, "올바른 유튜브 영상 주소가 아닙니다."),

    // ==== 재료 ====
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "재료를 찾을 수 없습니다."),
    DUPLICATE_INGREDIENT_NAME(HttpStatus.CONFLICT, "이미 등록된 재료 이름입니다."),

    // ==== 레시피 재료 ====
    DUPLICATE_RECIPE_INGREDIENT(HttpStatus.CONFLICT, "레시피에 이미 등록된 재료입니다."),
    INVALID_INGREDIENT_AMOUNT(HttpStatus.BAD_REQUEST, "재료 필요량은 0보다 커야 합니다."),

    // ==== 자동 구성 요청 검증 ====
    AUTO_CART_EMPTY_MENU(HttpStatus.BAD_REQUEST, "메뉴를 하나 이상 선택해 주세요."),
    DUPLICATE_MENU_IN_REQUEST(HttpStatus.BAD_REQUEST, "같은 메뉴가 중복으로 선택되었습니다."),
    INVALID_SERVINGS(HttpStatus.BAD_REQUEST, "인분 수가 허용 범위를 벗어났습니다."),
    OPTIONAL_INGREDIENT_NOT_IN_RECIPE(HttpStatus.BAD_REQUEST, "선택한 재료가 해당 레시피의 선택 재료가 아닙니다."),

    // ===== 자동 구성 미리보기·확정 =====
    AUTO_CART_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "자동 구성 요청을 찾을 수 없습니다."),
    AUTO_CART_INVALID_STATUS(HttpStatus.CONFLICT, "확정할 수 없는 상태의 자동 구성 요청입니다."),
    NO_PURCHASABLE_INGREDIENTS(HttpStatus.CONFLICT, "재계산 결과 장바구니에 담을 수 있는 상품이 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return null;
    }

    @Override
    public String getCode() {
        return name();
    }
}
