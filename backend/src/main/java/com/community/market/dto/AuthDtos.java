package com.community.market.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            String realName,
            String phone) {}

    public record UserResponse(Long id, String username, String realName, String phone,
                               String role, Integer creditScore, boolean enabled,
                               boolean foodRiskFlag, Integer foodRiskCount,
                               boolean requirePreReview, String preReviewReason) {}

    public record LoginResponse(String token, UserResponse user) {}
}
