package org.shiyi.memo.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.admin.dto.AdminDtos;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.SimpleRateLimiter;
import org.shiyi.memo.common.WebUtils;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.security.AdminContext;
import org.shiyi.memo.security.JwtService;
import org.shiyi.memo.security.PublicApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/**
 * 后台自己的登录入口，用的是 application.yml 里配置的账号，不是备忘录用户。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AppProperties properties;
    private final JwtService jwtService;
    private final SimpleRateLimiter rateLimiter;

    @PublicApi
    @PostMapping("/login")
    public AdminDtos.LoginResponse login(@Valid @RequestBody AdminDtos.LoginRequest request,
                                        HttpServletRequest servletRequest, HttpServletResponse response) {
        String ip = WebUtils.clientIp(servletRequest);
        if (!rateLimiter.tryAcquire("admin:login:" + ip, 10, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("尝试次数过多，请稍后再试");
        }
        AppProperties.Admin admin = properties.getAdmin();
        if (!equalsSafely(admin.getUsername(), request.username())
                || !equalsSafely(admin.getPassword(), request.password())) {
            throw ApiException.unauthorized("账号或密码不正确");
        }
        long ttlSeconds = Duration.ofHours(admin.getTtlHours()).toSeconds();
        writeCookie(response, jwtService.issueAdmin(admin.getUsername()), ttlSeconds);
        return new AdminDtos.LoginResponse(admin.getUsername(), ttlSeconds);
    }

    @PublicApi
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        writeCookie(response, "", 0);
    }

    /** 当前登录的后台账号 */
    @GetMapping("/me")
    public Map<String, Object> me() {
        String adminName = AdminContext.get();
        if (adminName == null) {
            throw ApiException.unauthorized("请先登录后台");
        }
        return Map.of("username", adminName);
    }

    private void writeCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        AppProperties.Admin admin = properties.getAdmin();
        ResponseCookie cookie = ResponseCookie.from(admin.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.getJwt().isSecureCookie())
                .path("/")
                .maxAge(maxAgeSeconds <= 0 ? Duration.ZERO : Duration.ofSeconds(maxAgeSeconds))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean equalsSafely(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
