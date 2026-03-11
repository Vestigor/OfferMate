package io.github.vestigor.offermate.modules.auth.service;

import io.github.vestigor.offermate.modules.auth.model.dto.AuthResponse;

/**
 * Refresh Token 服务
 */
public interface RefreshTokenService {

    /**
     * 为用户创建 refresh token，存入 Redis
     */
    String createRefreshToken(Long userId, String username);

    /**
     * 使用 refresh token 换取新的 access token + refresh token（旋转刷新）
     */
    AuthResponse refresh(String refreshToken);

    /**
     * 撤销 refresh token + 将当前请求的 access token 加入黑名单
     */
    void revokeAndBlacklistCurrent(String username);

    /**
     * 仅撤销 refresh token
     */
    void revokeRefreshToken(String username);
}
