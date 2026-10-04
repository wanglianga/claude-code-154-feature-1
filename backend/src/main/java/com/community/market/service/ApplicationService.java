package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.*;
import com.community.market.domain.enums.*;
import com.community.market.dto.ApplyDtos;
import com.community.market.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final EventRepository eventRepository;
    private final StallRepository stallRepository;
    private final UserRepository userRepository;
    private final DonationRecordRepository donationRecordRepository;

    // ==================== 报名 ====================

    @Transactional
    public Application apply(Long vendorId, ApplyDtos.ApplyRequest req) {
        eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("活动不存在"));
        User vendor = userRepository.findById(vendorId).orElseThrow(() -> new BizException("摊主不存在"));
        if (vendor.getRole() != UserRole.VENDOR) {
            throw new BizException("只有摊主账号可以报名");
        }
        if (applicationRepository.findByEventIdAndVendorId(req.eventId(), vendorId).isPresent()) {
            throw new BizException("您已报名该活动，请勿重复报名");
        }
        // 上次活动存在现场违规处理记录（如换品被下架/食品安全风险事件）→ 本次报名需提前复核
        if (vendor.isNextApplyReviewRequired()) {
            throw new BizException("您在上次活动中存在现场违规处理记录，本次报名需提前复核，请联系社区管理员完成复核后再报名");
        }
        // 食品安全风险事件影响后续食品类报名
        if (req.category() == StallType.FOOD && vendor.getFoodRiskLevel() != null && vendor.getFoodRiskLevel() >= 2) {
            throw new BizException("您曾发生食品安全风险事件，暂不能报名食品类摊位，可选择其他品类");
        }
        if (req.donationRate() == null || req.donationRate().signum() < 0
                || req.donationRate().compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("公益捐赠比例需在 0-100 之间");
        }
        if (req.category() == StallType.FOOD && req.foodQualification() == FoodQualStatus.NONE) {
            throw new BizException("食品类摊位必须填写食品资质状态");
        }

        Application a = new Application();
        a.setEventId(req.eventId());
        a.setVendorId(vendorId);
        a.setShopName(req.shopName());
        a.setCategory(req.category());
        a.setGoodsDescription(req.goodsDescription());
        a.setFoodQualification(req.foodQualification());
        a.setFoodLicenseNo(req.foodLicenseNo());
        a.setDonationRate(req.donationRate());
        a.setPowerNeeded(req.powerNeeded());
        a.setPowerWanted(req.powerWanted() == null ? 0 : req.powerWanted());
        a.setTableNeeded(req.tableNeeded());
        a.setTableCount(req.tableCount() == null ? 0 : req.tableCount());
        a.setChairNeeded(req.chairNeeded());
        a.setChairCount(req.chairCount() == null ? 0 : req.chairCount());
        a.setFamilyFriendly(req.familyFriendly());
        a.setNoiseMaking(req.noiseMaking());
        a.setPaymentMethod(req.paymentMethod() == null ? "CASH" : req.paymentMethod());
        a.setPaymentAccount(req.paymentAccount());
        return applicationRepository.save(a);
    }

    // ==================== 审核 ====================

    @Transactional
    public Application review(Long reviewerId, Long applicationId, boolean approved, String comment) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> new BizException("报名不存在"));
        if (a.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new BizException("该报名已审核，不可重复审核");
        }
        if (approved) {
            a.setStatus(ApplicationStatus.APPROVED);
            a.setCheckinCode(generateCode(a));
        } else {
            a.setStatus(ApplicationStatus.REJECTED);
        }
        a.setReviewerId(reviewerId);
        a.setReviewComment(comment);
        a.setReviewedAt(java.time.LocalDateTime.now());
        return applicationRepository.save(a);
    }

    private String generateCode(Application a) {
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "CM" + a.getEventId() + "-" + a.getId() + "-" + rand;
    }

    // ==================== 智能摊位分配 ====================

    /**
     * 分配依据：摊位大小、人流、用电、噪音、同类品类冲突、公益属性，
     * 并结合摊主信用、历史捐赠、食品风险。
     */
    @Transactional
    public List<Map<String, Object>> autoAllocate(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new BizException("活动不存在"));
        List<Application> apps = applicationRepository.findByEventIdAndStatus(eventId, ApplicationStatus.APPROVED);
        List<Stall> stalls = stallRepository.findByEventIdOrderByCodeAsc(eventId);
        if (stalls.isEmpty()) {
            throw new BizException("请先为该活动设置摊位平面图");
        }

        // 待分配：通过审核且尚未分配
        List<Application> pending = apps.stream()
                .filter(a -> a.getStallId() == null)
                .sorted(Comparator.comparingInt((Application a) -> applicantScore(a, null)).reversed())
                .collect(Collectors.toList());

        Set<Long> occupied = stalls.stream()
                .filter(s -> "ASSIGNED".equals(s.getStatus()) && s.getAssignedApplicationId() != null)
                .map(Stall::getId).collect(Collectors.toSet());

        // 已占用摊位 -> 品类，用于同类相邻冲突判断
        Map<Long, StallType> occupiedCategory = new HashMap<>();
        for (Stall s : stalls) {
            if (s.getAssignedApplicationId() != null) {
                applicationRepository.findById(s.getAssignedApplicationId())
                        .ifPresent(x -> occupiedCategory.put(s.getId(), x.getCategory()));
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Application a : pending) {
            Stall best = null;
            int bestScore = Integer.MIN_VALUE;
            List<String> bestReasons = new ArrayList<>();

            for (Stall s : stalls) {
                if (occupied.contains(s.getId())) {
                    continue;
                }
                List<String> veto = new ArrayList<>();
                int fit = stallFit(a, s, stalls, occupiedCategory, veto);
                if (!veto.isEmpty()) {
                    continue; // 硬性条件不满足（供电/噪音/食品区）
                }
                if (fit > bestScore) {
                    bestScore = fit;
                    best = s;
                    bestReasons = buildReasons(a, s, occupiedCategory, stalls);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applicationId", a.getId());
            row.put("shopName", a.getShopName());
            row.put("vendorId", a.getVendorId());
            row.put("category", a.getCategory().name());
            if (best == null) {
                row.put("allocated", false);
                row.put("reason", "没有满足供电/噪音/食品区等硬性条件的空闲摊位");
            } else {
                int score = applicantScore(a, event);
                a.setStallId(best.getId());
                a.setAllocationScore(score);
                a.setAllocationReason(String.join("；", bestReasons));
                applicationRepository.save(a);
                best.setStatus("ASSIGNED");
                best.setAssignedApplicationId(a.getId());
                stallRepository.save(best);
                occupied.add(best.getId());
                occupiedCategory.put(best.getId(), a.getCategory());
                row.put("allocated", true);
                row.put("stallId", best.getId());
                row.put("stallCode", best.getCode());
                row.put("zone", best.getZone());
                row.put("score", score);
                row.put("reason", String.join("；", bestReasons));
            }
            results.add(row);
        }
        return results;
    }

    /** 摊主综合优先级分：信用 + 历史捐赠 + 本次公益比例 + 亲子加成 - 食品风险 */
    public int applicantScore(Application a, Event event) {
        User vendor = userRepository.findById(a.getVendorId()).orElse(null);
        int credit = vendor == null || vendor.getCreditScore() == null ? 100 : vendor.getCreditScore();
        int score = credit;

        // 本次承诺捐赠比例（每 10% 加 3 分）
        score += a.getDonationRate().divide(new BigDecimal("10"), 0, java.math.RoundingMode.DOWN).intValue() * 3;

        // 历史捐赠：查该摊主在其他活动的已核对捐赠
        List<Application> mine = applicationRepository.findByVendorIdOrderByIdDesc(a.getVendorId());
        BigDecimal history = BigDecimal.ZERO;
        for (Application old : mine) {
            if (Objects.equals(old.getId(), a.getId())) {
                continue;
            }
            history = history.add(donationRecordRepository.findByApplicationId(old.getId())
                    .map(DonationRecord::getActualAmount).orElse(BigDecimal.ZERO));
        }
        score += Math.min(20, history.divide(new BigDecimal("100"), 0, java.math.RoundingMode.DOWN).intValue());

        if (a.isFamilyFriendly()) {
            score += 2;
        }
        // 食品资质不完整是风险项，分配降权（且不得进食品区）
        if (a.getFoodQualification() == FoodQualStatus.INCOMPLETE) {
            score -= 15;
        }
        return score;
    }

    /** 摊位匹配分；veto 命中表示硬性不符 */
    private int stallFit(Application a, Stall s, List<Stall> all, Map<Long, StallType> occupiedCategory, List<String> veto) {
        int score = 0;

        // 硬约束：用电
        if (a.isPowerNeeded()) {
            if (!s.isPowerAvailable() || s.getPowerCapacity() < nz(a.getPowerWanted())) {
                veto.add("供电不足");
                return 0;
            }
            score += 10;
        }
        // 硬约束：噪音（音响/叫卖不得放在噪音敏感区）
        if (a.isNoiseMaking() && s.isNoiseSensitive()) {
            veto.add("噪音敏感区");
            return 0;
        }
        // 硬约束：食品摊位进入食品区
        if (a.getCategory() == StallType.FOOD && !"食品区".equals(s.getZone())) {
            veto.add("非食品区");
            return 0;
        }

        // 摊位大小匹配：亲子/商业/食品倾向大摊位
        int wantSize = (a.isFamilyFriendly() || a.getCategory() == StallType.COMMERCIAL
                || a.getCategory() == StallType.FOOD) ? 3 : 2;
        score += 10 - Math.abs(s.getSizeLevel() - wantSize) * 4;

        // 人流：公益义卖、高捐赠比例优先高人流；二手物品可接受低人流
        int trafficPref;
        if (a.getCategory() == StallType.CHARITY
                || a.getDonationRate().compareTo(new BigDecimal("20")) >= 0) {
            trafficPref = 3;
        } else if (a.getCategory() == StallType.SECONDHAND) {
            trafficPref = 1;
        } else {
            trafficPref = 2;
        }
        score += 10 - Math.abs(s.getTrafficLevel() - trafficPref) * 4;

        // 同类品类冲突：相邻摊位（曼哈顿距离 ≤1）出现同品类则重罚
        int adjSame = 0;
        for (Stall occ : all) {
            if (occupiedCategory.get(occ.getId()) == a.getCategory() && manhattan(occ, s) <= 1) {
                adjSame++;
            }
        }
        score -= adjSame * 25;

        // 食品资质不完整：尽量远离食品区中心（已限定只能进食品区时给低人流边缘位）
        if (a.getFoodQualification() == FoodQualStatus.INCOMPLETE) {
            score -= s.getTrafficLevel() * 3;
        }
        return score;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private int manhattan(Stall x, Stall y) {
        int rx = x.getRowNo() == null ? 0 : x.getRowNo();
        int cx = x.getColNo() == null ? 0 : x.getColNo();
        int ry = y.getRowNo() == null ? 0 : y.getRowNo();
        int cy = y.getColNo() == null ? 0 : y.getColNo();
        return Math.abs(rx - ry) + Math.abs(cx - cy);
    }

    private List<String> buildReasons(Application a, Stall s, Map<Long, StallType> occupiedCategory, List<Stall> all) {
        List<String> reasons = new ArrayList<>();
        reasons.add(s.getZone() + " " + s.getCode());
        reasons.add("人流等级" + s.getTrafficLevel());
        if (a.isPowerNeeded()) {
            reasons.add("可供电" + s.getPowerCapacity() + "W，满足申报" + nz(a.getPowerWanted()) + "W");
        }
        if (a.getCategory() == StallType.FOOD) {
            reasons.add("食品区经营，资质：" + a.getFoodQualification().getLabel());
        }
        if (a.getCategory() == StallType.CHARITY || a.getDonationRate().compareTo(new BigDecimal("20")) >= 0) {
            reasons.add("公益属性优先高人流动线");
        }
        boolean adjacent = occupiedCategory.entrySet().stream().anyMatch(e -> e.getValue() == a.getCategory()
                && all.stream().anyMatch(o -> Objects.equals(o.getId(), e.getKey()) && manhattan(o, s) <= 1));
        reasons.add(adjacent ? "周边存在同类摊位（需关注品类冲突）" : "与相邻摊位品类错开");
        if (a.isFamilyFriendly()) {
            reasons.add("亲子摊位安排大位");
        }
        return reasons;
    }

    // ==================== 手动调整 ====================

    @Transactional
    public Application reassign(Long applicationId, Long stallId) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> new BizException("报名不存在"));
        Stall target = stallRepository.findById(stallId).orElseThrow(() -> new BizException("摊位不存在"));
        if (!Objects.equals(target.getEventId(), a.getEventId())) {
            throw new BizException("摊位与报名不属于同一活动");
        }
        if ("ASSIGNED".equals(target.getStatus()) && !Objects.equals(target.getAssignedApplicationId(), applicationId)) {
            throw new BizException("目标摊位已被占用，请先释放");
        }

        // 释放原摊位
        if (a.getStallId() != null) {
            stallRepository.findById(a.getStallId()).ifPresent(old -> {
                old.setStatus("FREE");
                old.setAssignedApplicationId(null);
                stallRepository.save(old);
            });
        }
        a.setStallId(target.getId());
        a.setAllocationReason((a.getAllocationReason() == null ? "" : a.getAllocationReason())
                + "；管理员手动调整至 " + target.getCode());
        target.setStatus("ASSIGNED");
        target.setAssignedApplicationId(a.getId());
        stallRepository.save(target);
        return applicationRepository.save(a);
    }

    @Transactional
    public void releaseStall(Long applicationId) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> new BizException("报名不存在"));
        if (a.getStallId() != null) {
            stallRepository.findById(a.getStallId()).ifPresent(s -> {
                s.setStatus("FREE");
                s.setAssignedApplicationId(null);
                stallRepository.save(s);
            });
        }
        a.setStallId(null);
        applicationRepository.save(a);
    }
}
