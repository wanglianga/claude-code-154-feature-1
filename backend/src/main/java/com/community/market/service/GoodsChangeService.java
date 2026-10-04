package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.GoodsChangeRequest;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.*;
import com.community.market.dto.GoodsChangeDtos;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.GoodsChangeRequestRepository;
import com.community.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 现场换品审核：摊主现场临时增加/更换售卖品类（如二手玩具摊临时加售自制蛋糕）时，
 * 工作人员在平台记录换品内容、食品资质与摊位条件并作出处理：
 * - 资质/条件不足 → 要求下架，保留现场照片，登记已售数量与是否需要退款；
 * - 允许售卖 → 更新品类、用电与食品安全提示。
 * 处理结果影响摊主信用、下次报名是否需要提前复核；食品风险事件影响后续食品类报名。
 */
@Service
@RequiredArgsConstructor
public class GoodsChangeService {

    private final GoodsChangeRequestRepository changeRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final IncidentService incidentService;
    private final CreditService creditService;

    /** 工作人员登记现场换品（换品内容、食品资质、摊位条件） */
    @Transactional
    public GoodsChangeRequest submit(Long staffId, GoodsChangeDtos.SubmitRequest req) {
        Application a = applicationRepository.findById(req.applicationId())
                .orElseThrow(() -> new BizException("报名不存在"));
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BizException("该报名未通过审核，不能登记现场换品");
        }
        boolean pending = changeRepository.findByApplicationIdOrderByIdDesc(a.getId()).stream()
                .anyMatch(x -> x.getStatus() == ChangeReviewStatus.PENDING);
        if (pending) {
            throw new BizException("该摊位已有待审核的换品记录，请先完成审核");
        }

        boolean involvesFood = req.involvesFood() || req.newCategory() == StallType.FOOD;
        FoodQualStatus qual = req.foodQualification() == null ? FoodQualStatus.NONE : req.foodQualification();
        if (involvesFood && qual == FoodQualStatus.NONE) {
            throw new BizException("换品涉及食品制售，必须现场核验并登记食品资质状态");
        }
        if (req.newGoodsDescription() == null || req.newGoodsDescription().isBlank()) {
            throw new BizException("请填写换品内容");
        }

        GoodsChangeRequest r = new GoodsChangeRequest();
        r.setEventId(a.getEventId());
        r.setApplicationId(a.getId());
        r.setStaffId(staffId);
        r.setNewCategory(req.newCategory());
        r.setNewGoodsDescription(req.newGoodsDescription());
        r.setInvolvesFood(involvesFood);
        r.setFoodQualification(involvesFood ? qual : FoodQualStatus.NONE);
        r.setFoodLicenseNo(req.foodLicenseNo());
        r.setStallConditionOk(req.stallConditionOk());
        r.setStallConditionNote(req.stallConditionNote());
        r.setPowerNeeded(req.powerNeeded());
        r.setPowerWanted(req.powerWanted() == null ? 0 : req.powerWanted());
        return changeRepository.save(r);
    }

    /** 审核处理：允许售卖（更新品类/用电/食品安全提示）或要求下架（留照片、登记已售与退款） */
    @Transactional
    public GoodsChangeRequest review(Long reviewerId, Long id, GoodsChangeDtos.ReviewRequest req) {
        GoodsChangeRequest r = changeRepository.findById(id)
                .orElseThrow(() -> new BizException("换品记录不存在"));
        if (r.getStatus() != ChangeReviewStatus.PENDING) {
            throw new BizException("该换品记录已处理，不可重复审核");
        }
        Application a = applicationRepository.findById(r.getApplicationId())
                .orElseThrow(() -> new BizException("报名不存在"));

        if (req.decision() == ChangeDecision.APPROVE) {
            approve(r, a, req);
        } else {
            remove(r, a, req);
        }
        r.setReviewerId(reviewerId);
        r.setReviewedAt(LocalDateTime.now());
        r.setReviewComment(req.reviewComment());
        return changeRepository.save(r);
    }

    /** 允许售卖：更新报名档案的品类、用电与食品安全提示 */
    private void approve(GoodsChangeRequest r, Application a, GoodsChangeDtos.ReviewRequest req) {
        if (r.isInvolvesFood() && r.getFoodQualification() != FoodQualStatus.COMPLETE) {
            throw new BizException("食品资质不足，按规则不能允许售卖，请做下架处理");
        }
        if (!r.isStallConditionOk()) {
            throw new BizException("摊位条件不满足换品经营，按规则不能允许售卖，请做下架处理");
        }

        // 更新品类与售卖内容
        a.setCategory(r.getNewCategory());
        a.setGoodsDescription((a.getGoodsDescription() == null ? "" : a.getGoodsDescription())
                + "｜现场换品新增：" + r.getNewGoodsDescription());
        a.setActualGoods(r.getNewGoodsDescription());

        // 更新食品资质档案
        if (r.isInvolvesFood()) {
            a.setFoodQualification(r.getFoodQualification());
            a.setFoodLicenseNo(r.getFoodLicenseNo());
        }

        // 更新用电
        boolean powerChanged = a.isPowerNeeded() != r.isPowerNeeded()
                || (r.getPowerWanted() != null && !r.getPowerWanted().equals(a.getPowerWanted()));
        a.setPowerNeeded(r.isPowerNeeded());
        a.setPowerWanted(r.getPowerWanted() == null ? 0 : r.getPowerWanted());

        // 食品安全提示（涉及食品时必给，可自定义）
        if (r.isInvolvesFood()) {
            String notice = (req.foodSafetyNotice() == null || req.foodSafetyNotice().isBlank())
                    ? "现场制售食品须持有效证照经营，操作台保持清洁，生熟分开、留样备查，防蝇防尘设施到位，工作人员将不定时巡检。"
                    : req.foodSafetyNotice();
            r.setFoodSafetyNotice(notice);
            a.setFoodSafetyNotice(notice);
        }
        // 用电变更提示（功率变化时必给，可自定义）
        if (powerChanged) {
            String note = (req.powerUpdateNote() == null || req.powerUpdateNote().isBlank())
                    ? "换品后申报用电 " + a.getPowerWanted() + "W，须在摊位核定容量内使用，超载将要求撤减设备并登记异常。"
                    : req.powerUpdateNote();
            r.setPowerUpdateNote(note);
            a.setPowerUpdateNote(note);
        }
        applicationRepository.save(a);

        r.setStatus(ChangeReviewStatus.APPROVED);
        r.setDecision(ChangeDecision.APPROVE);

        // 信用：临时换品未提前申报，轻微扣分（允许售卖不代表无责）
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -2,
                "现场临时换品未提前申报（审核通过允许售卖「" + r.getNewGoodsDescription() + "」）");
    }

    /** 要求下架：保留现场照片，登记已售数量与是否需要退款，影响信用与后续报名 */
    private void remove(GoodsChangeRequest r, Application a, GoodsChangeDtos.ReviewRequest req) {
        List<String> photos = req.photoUrls() == null ? List.of()
                : req.photoUrls().stream().filter(p -> p != null && !p.isBlank()).toList();
        if (photos.isEmpty()) {
            throw new BizException("下架处理必须保留现场照片，请填写照片（URL/文件名）");
        }
        if (req.soldQuantity() == null || req.soldQuantity() < 0) {
            throw new BizException("请登记下架前已售数量（可为 0）");
        }
        if (req.refundNeeded() == null) {
            throw new BizException("请登记是否需要退款");
        }

        r.setStatus(ChangeReviewStatus.REMOVED);
        r.setDecision(ChangeDecision.REMOVE);
        r.setPhotoUrls(String.join(",", photos));
        r.setSoldQuantity(req.soldQuantity());
        r.setRefundNeeded(req.refundNeeded());
        r.setRefundNote(req.refundNote());
        r.setRemovedAt(LocalDateTime.now());

        // 异常档案：换品下架
        incidentService.raise(a.getEventId(), a.getId(), IncidentType.GOODS_CHANGE_REMOVED,
                "现场换品「" + r.getNewGoodsDescription() + "」因"
                        + (r.isInvolvesFood() && r.getFoodQualification() != FoodQualStatus.COMPLETE ? "食品资质不足" : "摊位条件不满足")
                        + "被下架；已售 " + req.soldQuantity() + " 件，"
                        + (Boolean.TRUE.equals(req.refundNeeded()) ? "需退款（" + (req.refundNote() == null ? "金额待核" : req.refundNote()) + "）" : "无需退款")
                        + "；现场照片已留档 " + photos.size() + " 张",
                3, r.getStaffId());

        // 信用与后续报名：扣分 + 下次报名需提前复核
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -10,
                "现场换品资质不足被下架（" + r.getNewGoodsDescription() + "）");
        User vendor = userRepository.findById(a.getVendorId()).orElse(null);
        if (vendor != null) {
            vendor.setNextApplyReviewRequired(true);
            // 涉及食品的下架计入食品风险（影响后续食品类报名审核）
            if (r.isInvolvesFood()) {
                vendor.setFoodRiskLevel(Math.max(vendor.getFoodRiskLevel() == null ? 0 : vendor.getFoodRiskLevel(), 1));
            }
            userRepository.save(vendor);
        }
    }

    public List<GoodsChangeRequest> listByEvent(Long eventId) {
        return changeRepository.findByEventIdOrderByIdDesc(eventId);
    }
}
