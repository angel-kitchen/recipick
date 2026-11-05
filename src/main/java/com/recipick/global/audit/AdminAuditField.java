package com.recipick.global.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;

public enum AdminAuditField {
    PRICE("price", Kind.NUMBER),
    DISCOUNT_RATE("discountRate", Kind.NUMBER),
    TOTAL_QUANTITY("totalQuantity", Kind.NUMBER),
    QUANTITY("quantity", Kind.NUMBER),
    SALE_UNIT_AMOUNT("saleUnitAmount", Kind.NUMBER),
    SERVICE_RADIUS_M("serviceRadiusM", Kind.NUMBER),
    LATITUDE("latitude", Kind.NUMBER),
    LONGITUDE("longitude", Kind.NUMBER),
    BASE_SERVINGS("baseServings", Kind.NUMBER),
    COOKING_TIME_MINUTES("cookingTimeMinutes", Kind.NUMBER),
    SPICY_LEVEL("spicyLevel", Kind.NUMBER),
    AMOUNT_PER_SERVING("amountPerServing", Kind.NUMBER),
    INGREDIENT_ID("ingredientId", Kind.NUMBER),
    INGREDIENT_COUNT("ingredientCount", Kind.NUMBER),
    CATEGORY_ID("categoryId", Kind.NUMBER),
    REFUND_AMOUNT("refundAmount", Kind.NUMBER),
    ATTEMPT_COUNT("attemptCount", Kind.NUMBER),
    VERSION("version", Kind.NUMBER),
    REQUIRED("required", Kind.FLAG),
    AUTO_CART_AVAILABLE("autoCartAvailable", Kind.FLAG),
    DELETED("deleted", Kind.FLAG),
    ACTIVE("active", Kind.FLAG),
    AUTO_RENEW("autoRenew", Kind.FLAG),
    STATUS("status", Kind.ENUM),
    SALE_UNIT("saleUnit", Kind.ENUM),
    CUISINE_TYPE("cuisineType", Kind.ENUM),
    DISH_TYPE("dishType", Kind.ENUM),
    DIFFICULTY("difficulty", Kind.ENUM),
    TYPE("type", Kind.ENUM),
    SOURCE("source", Kind.ENUM),
    START_AT("startAt", Kind.TIME),
    END_AT("endAt", Kind.TIME),
    DISCOUNT_END_AT("discountEndAt", Kind.TIME);

    private final String key;
    private final Kind kind;

    AdminAuditField(String key, Kind kind) {
        this.key = key;
        this.kind = kind;
    }

    public String key() {
        return key;
    }

    public boolean supports(AdminAuditTarget target) {
        return switch (this) {
            case PRICE, DISCOUNT_RATE, DISCOUNT_END_AT, TOTAL_QUANTITY, QUANTITY, CATEGORY_ID, DELETED ->
                    target == AdminAuditTarget.PRODUCT;
            case SALE_UNIT, SALE_UNIT_AMOUNT -> target == AdminAuditTarget.PRODUCT
                    || target == AdminAuditTarget.INGREDIENT;
            case SERVICE_RADIUS_M, LATITUDE, LONGITUDE -> target == AdminAuditTarget.MART;
            case BASE_SERVINGS, COOKING_TIME_MINUTES, SPICY_LEVEL, AMOUNT_PER_SERVING, INGREDIENT_COUNT,
                    REQUIRED, AUTO_CART_AVAILABLE, CUISINE_TYPE, DISH_TYPE, DIFFICULTY ->
                    target == AdminAuditTarget.RECIPE;
            case INGREDIENT_ID -> target == AdminAuditTarget.PRODUCT || target == AdminAuditTarget.RECIPE;
            case REFUND_AMOUNT, ATTEMPT_COUNT -> target == AdminAuditTarget.PAYMENT;
            case AUTO_RENEW, SOURCE, START_AT, END_AT -> target == AdminAuditTarget.MEMBERSHIP;
            case TYPE -> target == AdminAuditTarget.MEMBERSHIP || target == AdminAuditTarget.AI_PROMPT;
            case VERSION -> target == AdminAuditTarget.AI_PROMPT;
            case ACTIVE -> target == AdminAuditTarget.RECIPE || target == AdminAuditTarget.PRODUCT;
            case STATUS -> target == AdminAuditTarget.MART || target == AdminAuditTarget.PRODUCT
                    || target == AdminAuditTarget.RECIPE || target == AdminAuditTarget.ORDER
                    || target == AdminAuditTarget.PAYMENT;
        };
    }

    public boolean accepts(Object value) {
        if (value == null) {
            return true;
        }
        return switch (kind) {
            case NUMBER -> value instanceof Number;
            case FLAG -> value instanceof Boolean;
            case ENUM -> value instanceof Enum<?>;
            case TIME -> value instanceof LocalDate || value instanceof LocalDateTime;
        };
    }

    private enum Kind {
        NUMBER, FLAG, ENUM, TIME
    }
}
