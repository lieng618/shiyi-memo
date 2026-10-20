package org.shiyi.memo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.shiyi.memo.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;
    private final long absoluteMaxSeconds;
    private final long refreshIntervalSeconds;
    private final boolean sliding;

    public JwtService(AppProperties properties) {
        String secret = properties.getJwt().getSecret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 个字节，当前 " + keyBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = Duration.ofHours(properties.getJwt().getTtlHours()).toSeconds();
        this.absoluteMaxSeconds = Duration.ofHours(properties.getJwt().getAbsoluteMaxHours()).toSeconds();
        this.refreshIntervalSeconds = Duration.ofHours(properties.getJwt().getRefreshIntervalHours()).toSeconds();
        this.sliding = properties.getJwt().isSliding();
        if (ttlSeconds <= 0 || absoluteMaxSeconds < ttlSeconds || refreshIntervalSeconds < 0) {
            throw new IllegalStateException(
                    "app.jwt 配置不合法：ttlHours 必须大于 0，absoluteMaxHours 不能小于 ttlHours，refreshIntervalHours 不能为负数");
        }
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /** 签发的令牌及其到期时间；Cookie 的 Max-Age 要和它对齐 */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    /** 用户登录：绝对到期时间从此刻重新开始计算 */
    public IssuedToken issue(long userId, String email, String role, long tokenVersion) {
        Instant now = Instant.now();
        return build(userId, email, role, tokenVersion, now, now.plusSeconds(absoluteMaxSeconds), ttlSeconds);
    }

    /** 后台管理员的令牌：固定有效期，不参与滑动续期，也不做版本吊销 */
    public IssuedToken issueAdmin(String username, long adminTtlSeconds) {
        Instant now = Instant.now();
        return build(0L, username, AuthUser.ROLE_ADMIN, 0L,
                now, now.plusSeconds(adminTtlSeconds), adminTtlSeconds);
    }

    /**
     * 滑动续期：只换一张新令牌，绝对到期时间沿用第一次登录时写进 abs 的值。
     * 这样一直使用可以免登录，但最多续到 absoluteMaxHours 为止。
     */
    public IssuedToken renew(AuthUser current, long tokenVersion) {
        Instant now = Instant.now();
        Instant absolute = current.absoluteExpiry() == null
                ? now.plusSeconds(absoluteMaxSeconds) : current.absoluteExpiry();
        return build(current.id(), current.email(), current.role(), tokenVersion, now, absolute, ttlSeconds);
    }

    /** 是否到了续期时间；没有签发时间的老令牌按「可以续」处理 */
    public boolean needsRenewal(AuthUser user) {
        if (!sliding || user == null) {
            return false;
        }
        // 升级前签发的令牌没有 abs：下一次访问强制换一次，直接进入新策略
        if (user.absoluteExpiry() == null) {
            return true;
        }
        Instant now = Instant.now();
        if (!now.isBefore(user.absoluteExpiry())) {
            return false;
        }
        return user.issuedAt() == null
                || !now.isBefore(user.issuedAt().plusSeconds(refreshIntervalSeconds));
    }

    private IssuedToken build(long userId, String email, String role, long tokenVersion,
                              Instant issuedAt, Instant absoluteExpiry, long ttl) {
        Instant expiresAt = issuedAt.plusSeconds(ttl);
        if (expiresAt.isAfter(absoluteExpiry)) {
            expiresAt = absoluteExpiry;
        }
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("ver", tokenVersion)
                .claim("abs", absoluteExpiry.getEpochSecond())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 解析 token，失败（过期 / 篡改 / 格式错误）一律返回 null。
     * 升级前签发的令牌没有 ver / abs：版本按 0 处理，abs 留空，
     * 让过滤器在用户下一次访问时把它平滑升级到新策略。
     */
    public AuthUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
            Object rawVersion = claims.get("ver");
            long version = rawVersion instanceof Number number ? number.longValue() : 0L;
            Object rawAbsolute = claims.get("abs");
            Instant absolute = rawAbsolute instanceof Number number
                    ? Instant.ofEpochSecond(number.longValue()) : null;
            return new AuthUser(Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    version,
                    issuedAt,
                    absolute);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
