package com.community.market.dto;

import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GoodsChangeDtos {

    /** 工作人员现场登记换品 */
    public record CreateRequest(
            @NotNull Long applicationId,
            @NotBlank String newGoods,
            String changeType,
            @NotNull StallType newCategory,
            boolean foodRelated,
            @NotNull FoodQualStatus foodQualification,
            String foodLicenseNo,
            Integer powerNeededWatt,
            boolean stallPowerAvailable,
            Integer stallPowerCapacity,
            boolean foodZone,
            boolean hygieneReady,
            String stallConditionNote,
            /** 现场照片（URL/文件名，多个逗号分隔）；下架处置要求必须保留 */
            String photoUrls,
            String remark) {}

    /** 审核处理 */
    public record ReviewRequest(
            /** APPROVED 允许售卖 / REMOVE 要求下架 */
            @NotBlank String decision,
            String foodSafetyNotice,
            String reviewComment,
            // ---- 下架处置字段 ----
            Integer soldQuantity,
            boolean refundNeeded,
            String refundNote) {}
}
