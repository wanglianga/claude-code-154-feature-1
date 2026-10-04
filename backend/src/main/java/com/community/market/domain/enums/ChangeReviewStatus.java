package com.community.market.domain.enums;

/** 现场换品审核状态 */
public enum ChangeReviewStatus {
    PENDING("待审核"),
    APPROVED("允许售卖"),
    REMOVED("已下架");

    private final String label;

    ChangeReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
