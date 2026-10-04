package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.GoodsChangeRequest;
import com.community.market.domain.entity.Incident;
import com.community.market.domain.entity.Stall;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.*;
import com.community.market.dto.GoodsChangeDtos;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.GoodsChangeRequestRepository;
import com.community.market.repository.StallRepository;
import com.community.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 现场换品审核：摊主现场临时增加/更换售卖内容（如二手玩具摊临时增加自制蛋糕）。
 * 工作人员记录换品内容、食品资质与摊位条件后给出审核决定：
 * - 允许售卖：更新报名品类、用电与食品安全提示，规范报备可记信用加分；
 * - 资质不足下架：保留现场照片、记录已售数量与退款、登记食品风险事件并扣信用，
 *   标记摊主下次报名需提前复核、后续食品类报名受限。
 */
@Service
@RequiredArgsConstructor
public class GoodsChangeService {

    private final GoodsChangeRequestRepository changeRepository;
    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final UserRepository userRepository;
    private final IncidentService incidentService;
    private final CreditService creditService;

    @Transactional
    public GoodsChangeRequest register(Long staffId, GoodsChangeDtos.CreateRequest req) {
        Application a = applicationRepository.findById(req.applicationId())
                .orElseThrow(() -> new BizException("报名不存在"));
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BizException("仅通过审核的报名可登记现场换品");
        }
        if (a.getCheckInStatus() != CheckInStatus.CHECKED_IN) {
            throw new BizException("摊主尚未核销入场，不能登记现场换品");
        }
        if (req.newGoods() == null || req.newGoods().isBlank()) {
            throw new BizException("请填写换品内容");
        }

        GoodsChangeRequest g = new GoodsChangeRequest();
        g.setEventId(a.getEventId());
        g.setApplicationId(a.getId());
        g.setVendorId(a.getVendorId());
        g.setNewGoods(req.newGoods());
        g.setChangeType("REPLACE".equalsIgnoreCase(req.changeType()) ? "REPLACE" : "ADD");
        g.setNewCategory(req.newCategory());
        g.setFoodRelated(req.foodRelated() || req.newCategory() == StallType.FOOD);
        g.setFoodQualification(req.foodQualification() == null ? FoodQualStatus.NONE : req.foodQualification());
        g.setFoodLicenseNo(req.foodLicenseNo());
        g.setPowerNeededWatt(req.powerNeededWatt() == null ? 0 : Math.max(0, req.powerNeededWatt()));

        // 摊位条件：以工作人员现场核验为准；未传时回填当前摊位快照
        g.setStallPowerAvailable(req.stallPowerAvailable());
        g.setStallPowerCapacity(req.stallPowerCapacity());
        g.setFoodZone(req.foodZone());
        g.setHygieneReady(req.hygieneReady());
        g.setStallConditionNote(req.stallConditionNote());
        if (a.getStallId() != null) {
            Stall s = stallRepository.findById(a.getStallId()).orElse(null);
            if (s != null) {
                // 现场核验结论优先（工作人员可据实取消勾选）；容量未填时用摊位档案回填供参考
                if ((g.getStallPowerCapacity() == null || g.getStallPowerCapacity() == 0)
                        && s.getPowerCapacity() != null) {
                    g.setStallPowerCapacity(s.getPowerCapacity());
                }
                if (!g.isFoodZone() && "食品区".equals(s.getZone())) {
                    g.setFoodZone(true);
                }
            }
        }
        g.setPhotoUrls(req.photoUrls());
        g.setRemark(req.remark());
        g.setStatus(ChangeReviewStatus.PENDING);
        g.setRaisedBy(staffId);
        g.setCreatedAt(LocalDateTime.now());
        return changeRepository.save(g);
    }

    /** 审核处理：APPROVED 允许售卖 / REMOVE 要求下架 */
    @Transactional
    public GoodsChangeRequest review(Long staffId, Long id, GoodsChangeDtos.ReviewRequest req) {
        GoodsChangeRequest g = changeRepository.findById(id)
                .orElseThrow(() -> new BizException("换品审核单不存在"));
        if (g.getStatus() != ChangeReviewStatus.PENDING) {
            throw new BizException("该换品申请已处理，不可重复审核");
        }
        Application a = applicationRepository.findById(g.getApplicationId())
                .orElseThrow(() -> new BizException("报名不存在"));

        boolean approve = "APPROVED".equalsIgnoreCase(req.decision());
        boolean remove = "REMOVE".equalsIgnoreCase(req.decision());
        if (!approve && !remove) {
            throw new BizException("审核决定不合法（APPROVED / REMOVE）");
        }

        g.setReviewedBy(staffId);
        g.setReviewedAt(LocalDateTime.now());
        g.setReviewComment(req.reviewComment());

        if (approve) {
            approveChange(g, a, req, staffId);
        } else {
            removeChange(g, a, req, staffId);
        }
        return changeRepository.save(g);
    }

    private void approveChange(GoodsChangeRequest g, Application a, GoodsChangeDtos.ReviewRequest req, Long staffId) {
        // 食品类换品：资质 + 摊位卫生条件 + 食品区 + 用电容量，全部满足才可放行
        if (g.isFoodRelated()) {
            if (g.getFoodQualification() != FoodQualStatus.COMPLETE) {
                throw new BizException("食品资质不齐全（" + g.getFoodQualification().getLabel()
                        + "），不允许现场售卖，应作下架处置并保留照片");
            }
            if (!g.isHygieneReady()) {
                throw new BizException("摊位不具备食品卫生操作条件（操作台/清洁用水/防尘防蝇），不能放行食品制售");
            }
            if (!g.isFoodZone()) {
                throw new BizException("该摊位不在食品区，食品制售需先由管理员改派至食品区摊位后再放行");
            }
        }
        int watt = g.getPowerNeededWatt() == null ? 0 : g.getPowerNeededWatt();
        if (watt > 0) {
            if (!g.isStallPowerAvailable()) {
                throw new BizException("摊位无供电条件，不允许新增用电设备");
            }
            int cap = g.getStallPowerCapacity() == null ? 0 : g.getStallPowerCapacity();
            if (cap > 0 && watt > cap) {
                throw new BizException("新增设备 " + watt + "W 超过摊位核定 " + cap + "W，不能放行，请减载或改派摊位");
            }
        }

        // 更新报名：品类
        a.setCategory(g.getNewCategory());
        // 同步食品资质（现场补验齐全）
        if (g.isFoodRelated()) {
            a.setFoodQualification(FoodQualStatus.COMPLETE);
            if (g.getFoodLicenseNo() != null && !g.getFoodLicenseNo().isBlank()) {
                a.setFoodLicenseNo(g.getFoodLicenseNo());
            }
        }
        // 更新用电：叠加新增设备功率
        if (watt > 0) {
            a.setPowerNeeded(true);
            a.setPowerWanted(Math.max(a.getPowerWanted() == null ? 0 : a.getPowerWanted(), watt));
        }
        // 食品安全提示
        String notice = req.foodSafetyNotice();
        if ((notice == null || notice.isBlank()) && g.isFoodRelated()) {
            notice = "现场新增自制食品：须持有效健康证、佩戴口罩手套，明码标价并保留食材采购凭证；"
                    + "防尘防蝇、生熟分开，当日剩余食品不得继续售卖；用电不超过摊位核定功率。";
        }
        if (notice != null && !notice.isBlank()) {
            g.setFoodSafetyNotice(notice);
            a.setFoodSafetyNotice(notice);
        }
        // 售卖内容补充
        String goods = a.getGoodsDescription() == null ? "" : a.getGoodsDescription();
        a.setGoodsDescription(goods + "；现场新增：" + g.getNewGoods());
        a.setActualGoods(a.getGoodsDescription());
        applicationRepository.save(a);

        g.setDecision("APPROVED");
        g.setStatus(ChangeReviewStatus.APPROVED);

        // 主动报备且资质齐全、配合管理 → 信用加分
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), 2,
                "现场换品主动报备，资质与摊位条件核验合格：" + g.getNewGoods());
    }

    private void removeChange(GoodsChangeRequest g, Application a, GoodsChangeDtos.ReviewRequest req, Long staffId) {
        if (g.getPhotoUrls() == null || g.getPhotoUrls().isBlank()) {
            throw new BizException("下架处置必须保留现场照片，请补充上传照片编号/链接");
        }
        int sold = req.soldQuantity() == null ? 0 : Math.max(0, req.soldQuantity());
        g.setSoldQuantity(sold);
        g.setRefundNeeded(req.refundNeeded());
        g.setRefundNote(req.refundNote());
        g.setDecision("REMOVE");
        g.setStatus(ChangeReviewStatus.REMOVED);

        String qualText = g.isFoodRelated() ? "食品资质：" + g.getFoodQualification().getLabel()
                + (g.getFoodLicenseNo() == null ? "" : "（" + g.getFoodLicenseNo() + "）") : "不具备食品制售条件";
        StringBuilder desc = new StringBuilder();
        desc.append("现场换品被责令下架：").append(g.getNewGoods())
                .append("；").append(qualText)
                .append("；下架前已售 ").append(sold).append(" 份")
                .append(req.refundNeeded() ? "，需退款处置" : "，无需退款");
        if (req.refundNote() != null && !req.refundNote().isBlank()) {
            desc.append("；退款说明：").append(req.refundNote());
        }
        Incident incident = incidentService.raise(a.getEventId(), a.getId(),
                IncidentType.FOOD_CHANGE_REMOVED, desc.toString(), 3, staffId);
        g.setIncidentId(incident.getId());

        // 信用：无资质制售食品 -15；已售出且需退款的风险更高，再 -5
        int delta = -15;
        String reason = "现场换品资质不足被责令下架：" + g.getNewGoods();
        if (sold > 0 && req.refundNeeded()) {
            delta -= 5;
            reason += "（已售并需退款）";
        }
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), delta, reason);

        // 食品风险标记 + 下次报名需提前复核
        User vendor = userRepository.findById(a.getVendorId()).orElse(null);
        if (vendor != null) {
            vendor.setFoodRiskFlag(true);
            vendor.setFoodRiskAt(LocalDateTime.now());
            vendor.setFoodRiskCount((vendor.getFoodRiskCount() == null ? 0 : vendor.getFoodRiskCount()) + 1);
            vendor.setRequirePreReview(true);
            vendor.setPreReviewReason("现场换品食品资质不足被下架（" + g.getNewGoods()
                    + "），下次报名需管理员提前复核，食品类报名暂受限制");
            userRepository.save(vendor);
        }

        // 报名上留下禁止性食品安全提示
        a.setFoodSafetyNotice("现场责令下架：未取得有效食品制售资质，本摊位禁止制售散装食品；"
                + "如再犯将取消后续活动报名资格。");
        applicationRepository.save(a);
    }

    /** 管理员提前复核通过：解除食品风险限制与下次报名强制提前复核标记 */
    @Transactional
    public User clearFoodRisk(Long vendorId, String reason) {
        User vendor = userRepository.findById(vendorId)
                .orElseThrow(() -> new BizException("摊主不存在"));
        vendor.setFoodRiskFlag(false);
        vendor.setRequirePreReview(false);
        vendor.setPreReviewReason(null);
        return userRepository.save(vendor);
    }

    // ==================== 视图 ====================

    public GoodsChangeRequest getById(Long id) {
        return changeRepository.findById(id)
                .orElseThrow(() -> new BizException("换品审核单不存在"));
    }

    public Map<String, Object> view(GoodsChangeRequest g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("eventId", g.getEventId());
        m.put("applicationId", g.getApplicationId());
        m.put("vendorId", g.getVendorId());
        Application a = g.getApplicationId() == null ? null
                : applicationRepository.findById(g.getApplicationId()).orElse(null);
        m.put("shopName", a == null ? null : a.getShopName());
        m.put("originalGoods", a == null ? null : a.getGoodsDescription());
        m.put("newGoods", g.getNewGoods());
        m.put("changeType", g.getChangeType());
        m.put("newCategory", g.getNewCategory().name());
        m.put("newCategoryLabel", g.getNewCategory().getLabel());
        m.put("foodRelated", g.isFoodRelated());
        m.put("foodQualification", g.getFoodQualification().name());
        m.put("foodQualificationLabel", g.getFoodQualification().getLabel());
        m.put("foodLicenseNo", g.getFoodLicenseNo());
        m.put("powerNeededWatt", g.getPowerNeededWatt());
        m.put("stallPowerAvailable", g.isStallPowerAvailable());
        m.put("stallPowerCapacity", g.getStallPowerCapacity());
        m.put("foodZone", g.isFoodZone());
        m.put("hygieneReady", g.isHygieneReady());
        m.put("stallConditionNote", g.getStallConditionNote());
        m.put("photoUrls", g.getPhotoUrls());
        m.put("remark", g.getRemark());
        m.put("status", g.getStatus().name());
        m.put("statusLabel", g.getStatus().getLabel());
        m.put("raisedBy", g.getRaisedBy());
        m.put("createdAt", g.getCreatedAt());
        m.put("reviewedBy", g.getReviewedBy());
        m.put("reviewedAt", g.getReviewedAt());
        m.put("decision", g.getDecision());
        m.put("foodSafetyNotice", g.getFoodSafetyNotice());
        m.put("reviewComment", g.getReviewComment());
        m.put("soldQuantity", g.getSoldQuantity());
        m.put("refundNeeded", g.isRefundNeeded());
        m.put("refundNote", g.getRefundNote());
        m.put("incidentId", g.getIncidentId());
        return m;
    }

    public List<GoodsChangeRequest> listByEvent(Long eventId) {
        return changeRepository.findByEventIdOrderByIdDesc(eventId);
    }

    public List<GoodsChangeRequest> listByVendor(Long vendorId) {
        return changeRepository.findByVendorIdOrderByIdDesc(vendorId);
    }

    public List<GoodsChangeRequest> listByApplication(Long applicationId) {
        return changeRepository.findByApplicationIdOrderByIdDesc(applicationId);
    }
}
