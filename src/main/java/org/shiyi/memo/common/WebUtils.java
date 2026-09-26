package org.shiyi.memo.common;

import jakarta.servlet.http.HttpServletRequest;

public final class WebUtils {

    private WebUtils() {
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For（Nginx 反代时由代理写入） */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
