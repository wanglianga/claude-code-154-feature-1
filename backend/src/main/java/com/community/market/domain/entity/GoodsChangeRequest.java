package com.community.market.domain.entity;

import com.community.market.domain.enums.ChangeDecision;
import com.community.market.domain.enums.ChangeReviewStatus;
import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 现场换品审核单：摊主现场临时增加/更换售卖品类时，
 * 工作人员在平台记录换品内容、食品资质与摊位条件，并给出允许售卖或下架的处理结论。
 * 处理结果影响摊主信用、下次报名是否需提前复核，以及后续食品类报名资格。
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

    /** 登记的工作人员 */
    private Long staffId;

    // ---------- 换品内容 ----------
    /** 换品后拟售卖的品类 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StallType newCategory;

    /** 换品内容描述（如：新增自制蛋糕） */
    @Column(nullable = false, length = 500)
    private String newGoodsDescription;

    /** 换品后是否涉及食品制售 */
    private boolean involvesFood;

    // ---------- 食品资质（现场核验） ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodQualStatus foodQualification = FoodQualStatus.NONE;

    /** 现场查验的证照编号/说明 */
    @Column(length = 200)
    private String foodLicenseNo;

    // ---------- 摊位条件 ----------
    /** 摊位条件是否满足换品经营（操作台/上下水/冷藏/防蝇防尘等） */
    private boolean stallConditionOk;

    /** 摊位条件现场记录 */
    @Column(length = 500)
    private String stallConditionNote;

    /** 换品后是否需要用电 */
    private boolean powerNeeded;

    /** 换品后申报功率（瓦） */
    private Integer powerWanted = 0;

    // ---------- 审核处理 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeReviewStatus status = ChangeReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ChangeDecision decision;

    @Column(length = 500)
    private String reviewComment;

    private Long reviewerId;
    private LocalDateTime reviewedAt;

    // ---------- 允许售卖后的现场提示 ----------
    /** 食品安全提示（涉及食品时生成/可编辑） */
    @Column(length = 500)
    private String foodSafetyNotice;

    /** 用电变更提示（功率变化时生成/可编辑） */
    @Column(length = 500)
    private String powerUpdateNote;

    // ---------- 下架处理 ----------
    /** 下架时已售数量 */
    private Integer soldQuantity;

    /** 是否需要退款 */
    private Boolean refundNeeded;

    /** 退款说明（金额/方式等） */
    @Column(length = 300)
    private String refundNote;

    /** 现场照片（留档，URL 或文件名，多张以逗号分隔） */
    @Column(length = 500)
    private String photoUrls;

    private LocalDateTime removedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
