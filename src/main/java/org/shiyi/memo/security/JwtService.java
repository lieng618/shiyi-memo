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

    public JwtService(AppProperties properties) {
        String secret = properties.getJwt().getSecret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 个字节，当前 " + keyBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = Duration.ofHours(properties.getJwt().getTtlHours()).toSeconds();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public String issue(long userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** 后台管理员的令牌：subject 固定 0，角色 ADMIN，和用户令牌用不同的 Cookie */
    public String issueAdmin(String username) {
        return issue(0L, username, AuthUser.ROLE_ADMIN);
    }

    /**
     * 解析 token，失败（过期 / 篡改 / 格式错误）一律返回 null。
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
            return new AuthUser(Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
