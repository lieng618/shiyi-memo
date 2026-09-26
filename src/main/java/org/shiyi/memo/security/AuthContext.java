package org.shiyi.memo.security;

import org.shiyi.memo.common.ApiException;

/**
 * 当前请求的登录用户，由 JwtAuthFilter 写入、请求结束时清理。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static AuthUser require() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }

    public static long requireUserId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
