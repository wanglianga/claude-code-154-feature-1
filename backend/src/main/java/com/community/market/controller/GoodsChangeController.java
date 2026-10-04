package com.community.market.controller;

import com.community.market.common.ApiResult;
import com.community.market.domain.entity.GoodsChangeRequest;
import com.community.market.domain.enums.UserRole;
import com.community.market.dto.GoodsChangeDtos;
import com.community.market.security.CurrentUser;
import com.community.market.security.TokenStore;
import com.community.market.service.GoodsChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 现场换品审核：工作人员登记摊主现场临时换品（内容/食品资质/摊位条件），
 * 审核允许售卖或责令下架；处理结果影响摊主信用、下次报名复核与后续食品类报名。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GoodsChangeController {

    private final GoodsChangeService goodsChangeService;
    private final TokenStore tokenStore;

    // ---------- 现场工作人员 ----------

    /** 登记现场换品 */
    @PostMapping("/onsite/goods-changes")
    public ApiResult<Map<String, Object>> register(@Valid @RequestBody GoodsChangeDtos.CreateRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        GoodsChangeRequest g = goodsChangeService.register(CurrentUser.userId(), req);
        return ApiResult.ok(goodsChangeService.view(g));
    }

    /** 活动下全部换品审核单 */
    @GetMapping("/onsite/events/{eventId}/goods-changes")
    public ApiResult<List<Map<String, Object>>> listByEvent(@PathVariable Long eventId,
                                                            @RequestParam(required = false) String status) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(goodsChangeService.listByEvent(eventId).stream()
                .filter(g -> status == null || status.isBlank() || g.getStatus().name().equals(status))
                .map(goodsChangeService::view)
                .toList());
    }

    @GetMapping("/onsite/goods-changes/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(goodsChangeService.view(goodsChangeService.getById(id)));
    }

    /** 审核：允许售卖（更新品类/用电/食品安全提示）或责令下架（照片/已售/退款/风险标记） */
    @PostMapping("/onsite/goods-changes/{id}/review")
    public ApiResult<Map<String, Object>> review(@PathVariable Long id,
                                                 @Valid @RequestBody GoodsChangeDtos.ReviewRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        GoodsChangeRequest g = goodsChangeService.review(CurrentUser.userId(), id, req);
        return ApiResult.ok(goodsChangeService.view(g));
    }

    // ---------- 摊主端 ----------

    /** 摊主查看自己的现场换品审核记录 */
    @GetMapping("/my/goods-changes")
    public ApiResult<List<Map<String, Object>>> mine() {
        tokenStore.requireRole(UserRole.VENDOR);
        return ApiResult.ok(goodsChangeService.listByVendor(CurrentUser.userId()).stream()
                .map(goodsChangeService::view).toList());
    }

    // ---------- 管理员 ----------

    /** 提前复核通过：解除摊主食品风险与下次报名强制复核标记 */
    @PostMapping("/admin/vendors/{vendorId}/clear-food-risk")
    public ApiResult<Void> clearFoodRisk(@PathVariable Long vendorId,
                                         @RequestBody(required = false) Map<String, String> body) {
        tokenStore.requireRole(UserRole.ADMIN);
        goodsChangeService.clearFoodRisk(vendorId, body == null ? null : body.get("reason"));
        return ApiResult.ok();
    }
}
