package org.shiyi.memo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.user.dto.UserDtos;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SendCodeRequest(@NotBlank @Email @Size(max = 160) String email) {
    }

    /**
     * @param devCode     只有在邮件服务未启用、且 app.mail.expose-code=true 时才有值，方便本地联调
     * @param expiresInSeconds 验证码有效期
     * @param registered  该邮箱是否已经注册过
     */
    public record SendCodeResponse(boolean sent, boolean mailEnabled, String devCode,
                                   int expiresInSeconds, boolean registered) {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 160) String email,
                               @NotBlank @Size(min = 4, max = 16) String code) {
    }

    /** 密码登录：邮箱必须已经设置过密码 */
    public record PasswordLoginRequest(@NotBlank @Email @Size(max = 160) String email,
                                       @NotBlank @Size(max = 72) String password) {
    }

    public record LoginResponse(UserDtos.UserDto user, long expiresInSeconds) {
    }
}
