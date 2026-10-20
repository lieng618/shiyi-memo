package org.shiyi.memo.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 统一写用户登录 Cookie，保证登录、滑动续期、清 Cookie 三处的属性完全一致。
 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    private final AppProperties properties;

    public void writeUserToken(HttpServletResponse response, JwtService.IssuedToken token) {
        Duration remaining = Duration.between(Instant.now(), token.expiresAt());
        write(response, token.value(), remaining.isNegative() ? Duration.ZERO : remaining);
    }

    /** 令牌已失效：顺手清掉浏览器里的 Cookie，免得每个请求都带着它 */
    public void clearUserToken(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    private void write(HttpServletResponse response, String token, Duration maxAge) {
        AppProperties.Jwt jwt = properties.getJwt();
        ResponseCookie cookie = ResponseCookie.from(jwt.getCookieName(), token)
                .httpOnly(true)
                .secure(jwt.isSecureCookie())
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
