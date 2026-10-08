package org.shiyi.memo.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.auth.dto.AuthDtos;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.SimpleRateLimiter;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.security.AuthUser;
import org.shiyi.memo.security.JwtService;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.dto.UserDtos;
import org.shiyi.memo.user.entity.AppUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 密码登录失败提示：按邮箱是否存在、是否设过密码分别给出下一步操作 */
    private static final String EMAIL_NOT_REGISTERED = "此邮箱未注册，用验证码登录会自动注册";
    private static final String PASSWORD_NOT_SET = "此邮箱未设置登录密码，请用验证码登录后再设置密码";
    private static final String PASSWORD_WRONG = "密码不正确，请重新输入";

    private final EmailCodeService emailCodeService;
    private final PasswordService passwordService;
    private final UserService userService;
    private final MailService mailService;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final SimpleRateLimiter rateLimiter;

    // ---------------- 验证码 ----------------

    public AuthDtos.SendCodeResponse sendCode(String rawEmail, String ip) {
        return emailCodeService.send(rawEmail, ip);
    }

    /** 验证码登录：邮箱没注册过就自动建号 */
    @Transactional
    public AuthDtos.LoginResponse login(String rawEmail, String inputCode, HttpServletResponse response) {
        String email = UserService.normalizeEmail(rawEmail);
        if (!rateLimiter.tryAcquire("login:email:" + email, 20, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("尝试次数过多，请稍后再试");
        }

        emailCodeService.verifyAndConsume(email, inputCode);

        AppUser user = userService.findOrCreateByEmail(email);
        return finishLogin(user, response, "验证码");
    }

    // ---------------- 密码 ----------------

    /** 密码登录：只有已经设置过密码的邮箱才能用，不会用密码自动建号 */
    @Transactional
    public AuthDtos.LoginResponse loginWithPassword(String rawEmail, String rawPassword, String ip,
                                                    HttpServletResponse response) {
        String email = UserService.normalizeEmail(rawEmail);
        if (!rateLimiter.tryAcquire("pwd:ip:" + ip, 20, Duration.ofMinutes(10))
                || !rateLimiter.tryAcquire("pwd:email:" + email, 10, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("尝试次数过多，请稍后再试");
        }

        AppUser user = userService.findByEmail(email);
        if (user == null) {
            // 照样走一次哈希校验，耗时和真实校验一致
            passwordService.wasteTime(rawPassword);
            log.warn("密码登录失败（邮箱未注册）email={}", email);
            throw ApiException.unauthorized(EMAIL_NOT_REGISTERED);
        }
        String hash = user.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            passwordService.wasteTime(rawPassword);
            log.warn("密码登录失败（未设置密码）email={}", email);
            throw ApiException.unauthorized(PASSWORD_NOT_SET);
        }
        if (!passwordService.matches(rawPassword, hash)) {
            log.warn("密码登录失败（密码不正确）email={}", email);
            throw ApiException.unauthorized(PASSWORD_WRONG);
        }
        return finishLogin(user, response, "密码");
    }

    /** 已登录用户直接设置 / 修改密码，不再重复要求邮箱验证码。 */
    @Transactional
    public UserDtos.UserDto changePassword(long userId, UserDtos.UpdatePasswordRequest request) {
        AppUser user = userService.requireEnabled(userId);
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();

        passwordService.validateStrength(request.newPassword(), user.getEmail());

        userService.updatePasswordHash(userId, passwordService.encode(request.newPassword()));
        mailService.sendPasswordChanged(user.getEmail(), hasPassword);
        log.info("用户{}登录密码 id={} email={}", hasPassword ? "修改了" : "设置了", userId, user.getEmail());
        return UserDtos.UserDto.from(userService.get(userId), userService.levelOf(user));
    }

    // ---------------- 通用 ----------------

    public void logout(HttpServletResponse response) {
        writeAuthCookie(response, "");
    }

    public UserDtos.UserDto currentUser() {
        AppUser user = userService.requireEnabled(org.shiyi.memo.security.AuthContext.requireUserId());
        return UserDtos.UserDto.from(user, userService.levelOf(user));
    }

    private AuthDtos.LoginResponse finishLogin(AppUser user, HttpServletResponse response, String method) {
        if (!user.isEnabled()) {
            throw ApiException.forbidden("账号已被禁用，请联系管理员");
        }
        userService.touchLastLogin(user.getId());
        // 重新读一次：created_at / last_login_at 是数据库默认值和刚刚那次 update 写进去的
        AppUser fresh = userService.get(user.getId());
        writeAuthCookie(response, jwtService.issue(fresh.getId(), fresh.getEmail(), AuthUser.ROLE_USER));
        log.info("用户登录成功 id={} email={} 方式={}", fresh.getId(), fresh.getEmail(), method);
        return new AuthDtos.LoginResponse(UserDtos.UserDto.from(fresh, userService.levelOf(fresh)),
                jwtService.getTtlSeconds());
    }

    private void writeAuthCookie(HttpServletResponse response, String token) {
        AppProperties.Jwt jwt = properties.getJwt();
        ResponseCookie cookie = ResponseCookie.from(jwt.getCookieName(), token)
                .httpOnly(true)
                .secure(jwt.isSecureCookie())
                .path("/")
                .maxAge(token.isEmpty() ? Duration.ZERO : Duration.ofSeconds(jwtService.getTtlSeconds()))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
