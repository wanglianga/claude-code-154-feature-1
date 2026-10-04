package com.community.market.domain.enums;

/** 现场换品审核结论 */
public enum ChangeDecision {
    APPROVE("允许售卖"),
    REMOVE("要求下架");

    private final String label;

    ChangeDecision(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
