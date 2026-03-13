package io.github.vestigor.offermate.modules.auth.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.security.JwtService;
import io.github.vestigor.offermate.infrastructure.redis.RedisService;
import io.github.vestigor.offermate.modules.auth.model.dto.AuthResponse;
import io.github.vestigor.offermate.modules.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh Token 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RedisService redisService;
    private final JwtService jwtService;
    private final HttpServletRequest httpServletRequest;

    /**
     * Redis key 前缀：refresh token → 用户信息
     */
    private static final String RT_PREFIX = "auth:refresh:";

    /**
     * Redis key 前缀：username → refresh token
     */
    private static final String RT_USER_PREFIX = "auth:refresh:user:";

    /**
     * Refresh token 有效期，默认 7 天
     */
    @Value("${app.jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    @Override
    public String createRefreshToken(Long userId, String username) {
        // 先清除该用户之前的 refresh token
        revokeRefreshToken(username);

        String refreshToken = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMillis(refreshExpiration);

        // 正向：token → "userId:username"
        redisService.set(RT_PREFIX + refreshToken, userId + ":" + username, ttl);
        // 反向：username → token
        redisService.set(RT_USER_PREFIX + username, refreshToken, ttl);

        log.debug("已创建 refresh token: username={}, ttl={}ms", username, refreshExpiration);
        return refreshToken;
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token 不能为空");
        }

        String tokenKey = RT_PREFIX + refreshToken;

        // 查找 refresh token
        String stored;
        try {
            stored = (String) redisService.get(tokenKey);
        } catch (Exception e) {
            log.warn("读取 refresh token 失败", e);
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Refresh token 无效");
        }

        if (stored == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "Refresh token 已过期或已被撤销");
        }

        // 解析用户信息
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) {
            log.error("Refresh token 存储值格式异常: {}", stored);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        Long userId = Long.parseLong(parts[0]);
        String username = parts[1];

        // 删除旧的 refresh token（旋转策略：用过即废）
        redisService.delete(tokenKey);

        // 签发新的 access token + refresh token
        String newAccessToken = jwtService.generateToken(userId, username);
        String newRefreshToken = createRefreshToken(userId, username);

        log.info("Token 刷新成功: userId={}, username={}", userId, username);

        return new AuthResponse(newAccessToken, newRefreshToken, userId, username);
    }

    @Override
    public void revokeAndBlacklistCurrent(String username) {
        blacklistCurrentAccessToken();
        revokeRefreshToken(username);
    }

    @Override
    public void revokeRefreshToken(String username) {
        String userKey = RT_USER_PREFIX + username;
        try {
            String existingToken = (String) redisService.get(userKey);
            if (existingToken != null) {
                redisService.delete(RT_PREFIX + existingToken);
                redisService.delete(userKey);
                log.debug("已撤销 refresh token: username={}", username);
            }
        } catch (Exception e) {
            log.warn("撤销 refresh token 时发生异常: username={}", username, e);
            throw e;
        }
    }

    private void blacklistCurrentAccessToken() {
        String bearerToken = httpServletRequest.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            jwtService.blacklistToken(bearerToken.substring(7));
        }
    }
}