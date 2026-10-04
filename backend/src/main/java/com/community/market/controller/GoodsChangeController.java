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

/** 现场换品审核：登记换品（内容/食品资质/摊位条件）→ 审核（允许售卖 / 要求下架） */
@RestController
@RequestMapping("/api/onsite/goods-changes")
@RequiredArgsConstructor
public class GoodsChangeController {

    private final GoodsChangeService goodsChangeService;
    private final TokenStore tokenStore;

    /** 工作人员登记现场换品 */
    @PostMapping
    public ApiResult<GoodsChangeRequest> submit(@Valid @RequestBody GoodsChangeDtos.SubmitRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(goodsChangeService.submit(CurrentUser.userId(), req));
    }

    /** 活动维度的换品审核记录 */
    @GetMapping("/events/{eventId}")
    public ApiResult<List<GoodsChangeRequest>> list(@PathVariable Long eventId) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(goodsChangeService.listByEvent(eventId));
    }

    /** 审核处理：允许售卖（更新品类/用电/食品安全提示）或要求下架（照片留档、已售数量、退款） */
    @PostMapping("/{id}/review")
    public ApiResult<GoodsChangeRequest> review(@PathVariable Long id,
                                                @Valid @RequestBody GoodsChangeDtos.ReviewRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(goodsChangeService.review(CurrentUser.userId(), id, req));
    }
}
