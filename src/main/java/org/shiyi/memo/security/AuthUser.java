package org.shiyi.memo.security;

import java.time.Instant;

/**
 * 从令牌里解析出来的身份。
 *
 * @param tokenVersion 签发时的令牌版本，和用户表当前的版本不一致就说明已被吊销
 * @param issuedAt     本次签发时间，滑动续期按它判断多久没续过
 * @param absoluteExpiry 本次登录的绝对到期时间，续期时保持不变；升级前的旧令牌为 null
 */
public record AuthUser(long id, String email, String role, long tokenVersion,
                       Instant issuedAt, Instant absoluteExpiry) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    /** 旧代码和三方脚本可能只关心身份本身 */
    public AuthUser(long id, String email, String role) {
        this(id, email, role, 0L, null, null);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
