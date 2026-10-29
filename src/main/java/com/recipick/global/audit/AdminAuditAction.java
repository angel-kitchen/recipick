package com.recipick.global.audit;

public enum AdminAuditAction {
    MEMBERSHIP_GRANT(AdminAuditTarget.MEMBERSHIP, false, false, false),
    MEMBER_LIST_VIEW(AdminAuditTarget.MEMBER, true, false, false),
    MEMBER_MEMBERSHIP_HISTORY_VIEW(AdminAuditTarget.MEMBER, false, false, false),
    MART_CREATE(AdminAuditTarget.MART, false, true, false),
    MART_UPDATE(AdminAuditTarget.MART, false, true, false),
    MART_STATUS_CHANGE(AdminAuditTarget.MART, false, true, true),
    CATEGORY_CREATE(AdminAuditTarget.CATEGORY, false, false, false),
    CATEGORY_UPDATE(AdminAuditTarget.CATEGORY, false, false, false),
    CATEGORY_DELETE(AdminAuditTarget.CATEGORY, false, false, false),
    PRODUCT_CREATE(AdminAuditTarget.PRODUCT, false, true, true),
    PRODUCT_UPDATE(AdminAuditTarget.PRODUCT, false, true, true),
    PRODUCT_DELETE(AdminAuditTarget.PRODUCT, false, true, true),
    PRODUCT_PRICE_CHANGE(AdminAuditTarget.PRODUCT, false, true, true),
    PRODUCT_STATUS_CHANGE(AdminAuditTarget.PRODUCT, false, true, true),
    PRODUCT_INGREDIENT_CHANGE(AdminAuditTarget.PRODUCT, false, true, true),
    STOCK_ADJUST(AdminAuditTarget.PRODUCT, false, true, true),
    RECIPE_CREATE(AdminAuditTarget.RECIPE, false, false, false),
    RECIPE_UPDATE(AdminAuditTarget.RECIPE, false, false, false),
    RECIPE_STATUS_CHANGE(AdminAuditTarget.RECIPE, false, false, false),
    RECIPE_INGREDIENTS_CHANGE(AdminAuditTarget.RECIPE, false, false, false),
    RECIPE_AUTO_CART_CHANGE(AdminAuditTarget.RECIPE, false, false, false),
    INGREDIENT_CREATE(AdminAuditTarget.INGREDIENT, false, false, false),
    INGREDIENT_UPDATE(AdminAuditTarget.INGREDIENT, false, false, false),
    ORDER_ACCEPT(AdminAuditTarget.ORDER, false, true, true),
    ORDER_REJECT(AdminAuditTarget.ORDER, false, true, true),
    ORDER_DELIVERY_CHANGE(AdminAuditTarget.ORDER, false, true, true),
    ORDER_LIST_VIEW(AdminAuditTarget.ORDER, true, false, true),
    ORDER_DETAIL_VIEW(AdminAuditTarget.ORDER, false, true, true),
    REFUND_RETRY(AdminAuditTarget.PAYMENT, false, true, false),
    PAYMENT_LIST_VIEW(AdminAuditTarget.PAYMENT, true, false, false),
    PAYMENT_DETAIL_VIEW(AdminAuditTarget.PAYMENT, false, true, false),
    POINT_BALANCE_VIEW(AdminAuditTarget.MEMBER, false, false, false),
    POINT_HISTORY_VIEW(AdminAuditTarget.MEMBER, false, false, false),
    AI_REQUEST_LIST_VIEW(AdminAuditTarget.AI_REQUEST, true, false, false);

    private final AdminAuditTarget target;
    private final boolean targetOptional;
    private final boolean martRequired;
    private final boolean martAdminAllowed;

    AdminAuditAction(AdminAuditTarget target, boolean targetOptional, boolean martRequired, boolean martAdminAllowed) {
        this.target = target;
        this.targetOptional = targetOptional;
        this.martRequired = martRequired;
        this.martAdminAllowed = martAdminAllowed;
    }

    public AdminAuditTarget target() {
        return target;
    }

    public boolean targetOptional() {
        return targetOptional;
    }

    public boolean martRequired() {
        return martRequired;
    }

    public boolean martAdminAllowed() {
        return martAdminAllowed;
    }
}
