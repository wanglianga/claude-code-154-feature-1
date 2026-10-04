package com.community.market.dto;

import com.community.market.domain.enums.ChangeDecision;
import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 现场换品审核相关请求 */
public class GoodsChangeDtos {

    /** 工作人员登记换品：换品内容、食品资质、摊位条件 */
    public record SubmitRequest(
            @NotNull Long applicationId,
            @NotNull StallType newCategory,
            @NotNull String newGoodsDescription,
            boolean involvesFood,
            FoodQualStatus foodQualification,
            String foodLicenseNo,
            boolean stallConditionOk,
            String stallConditionNote,
            boolean powerNeeded,
            Integer powerWanted) {}

    /** 审核处理：允许售卖 / 要求下架 */
    public record ReviewRequest(
            @NotNull ChangeDecision decision,
            String reviewComment,
            /** 允许售卖时可自定义食品安全提示（留空则系统生成） */
            String foodSafetyNotice,
            /** 允许售卖时可自定义用电变更提示（留空则系统生成） */
            String powerUpdateNote,
            /** 下架时必填：现场照片（留档） */
            List<String> photoUrls,
            /** 下架时登记：已售数量 */
            Integer soldQuantity,
            /** 下架时登记：是否需要退款 */
            Boolean refundNeeded,
            String refundNote) {}
}
