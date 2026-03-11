package io.github.vestigor.offermate.modules.auth.model.dto;

/**
 * 认证响应
 */
public record AuthResponse(
        String token,
        String refreshToken,
        Long userId,
        String username
){}