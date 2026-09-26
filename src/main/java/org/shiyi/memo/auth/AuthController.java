package org.shiyi.memo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.auth.dto.AuthDtos;
import org.shiyi.memo.common.WebUtils;
import org.shiyi.memo.security.AuthContext;
import org.shiyi.memo.security.PublicApi;
import org.shiyi.memo.user.dto.UserDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 发送登录 / 注册验证码 */
    @PublicApi
    @PostMapping("/code")
    public AuthDtos.SendCodeResponse sendCode(@Valid @RequestBody AuthDtos.SendCodeRequest request,
                                              HttpServletRequest servletRequest) {
        return authService.sendCode(request.email(), WebUtils.clientIp(servletRequest));
    }

    /** 校验验证码并登录，首次登录的邮箱会自动注册 */
    @PublicApi
    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                        HttpServletResponse response) {
        return authService.login(request.email(), request.code(), response);
    }

    /** 邮箱 + 密码登录（需要先在个人中心设置过密码） */
    @PublicApi
    @PostMapping("/login/password")
    public AuthDtos.LoginResponse loginWithPassword(@Valid @RequestBody AuthDtos.PasswordLoginRequest request,
                                                   HttpServletRequest servletRequest,
                                                   HttpServletResponse response) {
        return authService.loginWithPassword(request.email(), request.password(),
                WebUtils.clientIp(servletRequest), response);
    }

    /** 设置 / 修改登录密码：登录态直接修改 */
    @PutMapping("/password")
    public UserDtos.UserDto changePassword(@Valid @RequestBody UserDtos.UpdatePasswordRequest request) {
        return authService.changePassword(AuthContext.requireUserId(), request);
    }

    @PublicApi
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authService.logout(response);
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public UserDtos.UserDto me() {
        return authService.currentUser();
    }

}
