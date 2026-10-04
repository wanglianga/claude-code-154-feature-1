package com.community.market.controller;

import com.community.market.common.ApiResult;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.UserRole;
import com.community.market.dto.AuthDtos;
import com.community.market.security.CurrentUser;
import com.community.market.security.TokenStore;
import com.community.market.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;
    private final TokenStore tokenStore;

    @GetMapping
    public ApiResult<List<User>> list(@RequestParam(required = false) String role) {
        tokenStore.requireRole(UserRole.ADMIN);
        UserRole r = null;
        if (role != null && !role.isBlank()) {
            r = UserRole.valueOf(role);
        }
        return ApiResult.ok(userService.list(r));
    }

    @PostMapping
    public ApiResult<User> create(@RequestParam UserRole role, @Valid @RequestBody AuthDtos.RegisterRequest req) {
        tokenStore.requireRole(UserRole.ADMIN);
        return ApiResult.ok(userService.adminCreate(role, req));
    }

    /** 完成提前复核：清除摊主「下次报名需提前复核」限制 */
    @PostMapping("/{id}/clear-review-flag")
    public ApiResult<User> clearReviewFlag(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN);
        return ApiResult.ok(userService.clearReviewFlag(id));
    }
}
