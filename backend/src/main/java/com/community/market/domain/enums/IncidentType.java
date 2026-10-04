package com.community.market.domain.enums;

/** 现场异常类型，覆盖需求中列举的问题 */
public enum IncidentType {
    FOOD_QUALIFICATION("食品资质不完整"),
    MISMATCH("现场售卖与报名不符"),
    CATEGORY_CONFLICT("同类摊位冲突"),
    POWER_OVERLOAD("用电超载"),
    EARLY_LEAVE("摊主提前离场"),
    DONATION_MISMATCH("捐赠金额与流水不一致"),
    PUBLIC_REQUEST("公益组织要求公示明细"),
    GOODS_CHANGE_REMOVED("换品资质不足被下架"),
    FOOD_RISK("食品安全风险事件"),
    OTHER("其他");

    private final String label;

    IncidentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
