package io.github.vestigor.offermate.common.security;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.infrastructure.redis.RedisService;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具服务
 */
@Slf4j
@Service
public class JwtService {

    private final RedisService redisService;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration:7200000}")
    private Long expiration;

    /**
     * Token 黑名单的 Redis 键前缀
     */
    private static final String TOKEN_BLACKLIST_PREFIX = "jwt:blacklist:";

    public JwtService(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 生成JWT签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT access token
     */
    public String generateToken(Long id, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", id);
        claims.put("username", username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 从Token中提取用户ID
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);

            Object raw = claims.get("userId");
            return ((Number) raw).longValue();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 从Token中提取用户名
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("username", String.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 获取 Token 剩余有效时间（毫秒），已过期返回 0
     */
    public long getRemainingExpiration(String token) {
        Claims claims = parseToken(token);
        Date expirationDate = claims.getExpiration();
        long remaining = expirationDate.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * 验证Token是否有效（签名 + 过期 + 黑名单）
     */
    public boolean validateToken(String token) {
        parseToken(token);
        if (isTokenBlacklisted(token)) {
            throw new BusinessException(ErrorCode.TOKEN_BLACKLISTED);
        }
        return true;
    }

    /**
     * 将 Token 加入黑名单，TTL 设为 Token 剩余有效期
     */
    public void blacklistToken(String token) {
        long remaining = getRemainingExpiration(token);
        if (remaining > 0) {
            String key = TOKEN_BLACKLIST_PREFIX + token;
            redisService.set(key, "1", Duration.ofMillis(remaining));
            log.info("Token已加入黑名单, 剩余有效期: {}ms", remaining);
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        return redisService.exists(key);
    }

    /**
     * 解析Token
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
