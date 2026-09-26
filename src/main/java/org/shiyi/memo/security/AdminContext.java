package org.shiyi.memo.security;

/**
 * 当前请求的后台管理员（独立站点的登录态），由 JwtAuthFilter 写入、请求结束时清理。
 */
public final class AdminContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(String adminName) {
        HOLDER.set(adminName);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
