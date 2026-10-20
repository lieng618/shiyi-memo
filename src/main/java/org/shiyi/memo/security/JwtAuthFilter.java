package org.shiyi.memo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.user.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 Cookie（浏览器）或 Authorization 头（脚本 / 移动端）里取出 token，放进 AuthContext。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** SSE 连接会长期挂着，不在它上面重签；下一次普通接口请求再续 */
    private static final String EVENT_STREAM_PATH = "/api/events/stream";

    private final JwtService jwtService;
    private final AppProperties properties;
    private final UserService userService;
    private final AuthCookies authCookies;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 静态资源和页面本身不需要身份，别让带着旧 Cookie 的请求白白查一次库
            if (!request.getRequestURI().startsWith("/api/")) {
                chain.doFilter(request, response);
                return;
            }

            // 后台令牌：只写 AdminContext，不参与备忘录用户的身份
            AuthUser admin = jwtService.parse(readCookie(request, properties.getAdmin().getCookieName()));
            if (admin != null && admin.isAdmin()) {
                AdminContext.set(admin.email());
            }

            AuthUser user = jwtService.parse(resolveUserToken(request));
            if (user != null && !user.isAdmin()) {
                // 版本对不上说明改过密码、被封号或用户已不存在，旧令牌立即失效
                long tokenVersion = userService.tokenVersionOf(user.id());
                if (tokenVersion >= 0 && user.tokenVersion() == tokenVersion) {
                    AuthContext.set(user);
                    renewIfNeeded(request, response, user, tokenVersion);
                } else {
                    authCookies.clearUserToken(response);
                }
            }
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
            AdminContext.clear();
        }
    }

    private String resolveUserToken(HttpServletRequest request) {
        String token = readCookie(request, properties.getJwt().getCookieName());
        if (token != null) {
            return token;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void renewIfNeeded(HttpServletRequest request, HttpServletResponse response,
                               AuthUser user, long tokenVersion) {
        if (request.getRequestURI().startsWith(EVENT_STREAM_PATH) || !jwtService.needsRenewal(user)) {
            return;
        }
        authCookies.writeUserToken(response, jwtService.renew(user, tokenVersion));
    }
}
