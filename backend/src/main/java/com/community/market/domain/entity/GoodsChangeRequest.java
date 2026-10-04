package com.community.market.domain.entity;

import com.community.market.domain.enums.ChangeReviewStatus;
import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 现场换品审核单：摊主现场临时增加/更换售卖内容（典型场景：二手玩具摊
 * 临时增加自制蛋糕），工作人员记录换品内容、食品资质与摊位条件后审核。
 *
 * 审核结果：
 * - 允许售卖：更新报名品类、用电需求与食品安全提示，记录信用加分/不扣分；
 * - 资质不足要求下架：保留现场照片，登记食品风险事件、扣信用，
 *   标记摊主下次报名需提前复核，并记录已售数量与是否退款。
 */
@Data
@Entity
@Table(name = "goods_change_request")
public class GoodsChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private Long applicationId;

    private Long vendorId;

    // ---------- 申请内容 ----------
    /** 新增/更换的售卖内容，如"自制奶油蛋糕、纸杯蛋糕" */
    @Column(nullable = false, length = 500)
    private String newGoods;

    /** 换品类型：ADD 临时新增 / REPLACE 更换卖品 */
    @Column(nullable = false, length = 20)
    private String changeType = "ADD";

    /** 换品后的品类（食品等） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StallType newCategory;

    /** 换品是否涉及制售食品 */
    private boolean foodRelated;

    /** 现场提交/核验的食品资质状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodQualStatus foodQualification = FoodQualStatus.NONE;

    /** 证照/健康证编号或办理情况说明 */
    @Column(length = 200)
    private String foodLicenseNo;

    /** 新增设备申报功率（瓦），无设备为 0 */
    private Integer powerNeededWatt = 0;

    // ---------- 摊位条件核验 ----------
    /** 摊位是否具备用电条件 */
    private boolean stallPowerAvailable;
    /** 摊位核定供电功率（瓦） */
    private Integer stallPowerCapacity;
    /** 是否食品区摊位 */
    private boolean foodZone;
    /** 是否具备卫生操作条件（操作台、清洁用水、防尘防蝇等） */
    private boolean hygieneReady;
    /** 摊位条件备注（空间、明火、相邻摊位等） */
    @Column(length = 500)
    private String stallConditionNote;

    // ---------- 证据 ----------
    /** 现场照片（URL 或文件名/编号，多个以逗号分隔） */
    @Column(length = 1000)
    private String photoUrls;

    @Column(length = 500)
    private String remark;

    // ---------- 审核 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeReviewStatus status = ChangeReviewStatus.PENDING;

    private Long raisedBy;
    private LocalDateTime createdAt;

    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    /** 审核决定：APPROVED 允许售卖 / REMOVE 要求下架 */
    @Column(length = 20)
    private String decision;

    /** 食品安全提示（允许售卖时展示给摊主与巡检人员） */
    @Column(length = 500)
    private String foodSafetyNotice;

    /** 审核处理意见 */
    @Column(length = 500)
    private String reviewComment;

    // ---------- 下架处置 ----------
    /** 下架前已售数量 */
    private Integer soldQuantity = 0;

    /** 是否需要/已为居民退款 */
    private boolean refundNeeded;

    /** 退款处置说明 */
    @Column(length = 300)
    private String refundNote;

    /** 关联的食品风险异常档案 ID */
    private Long incidentId;
}
