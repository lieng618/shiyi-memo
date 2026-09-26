package org.shiyi.memo.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.security.AuthContext;
import org.shiyi.memo.user.dto.UserDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 我的空间配额：等级、单文件上限、总上限、已用 */
    @GetMapping("/quota")
    public UserDtos.QuotaDto quota() {
        return userService.quotaOf(userService.requireEnabled(AuthContext.requireUserId()));
    }

    @PutMapping("/profile")
    public UserDtos.UserDto updateProfile(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        var user = userService.updateNickname(AuthContext.requireUserId(), request.nickname());
        return UserDtos.UserDto.from(user, userService.levelOf(user));
    }

    /** 换头像；fileId 传 null 表示恢复成昵称首字 */
    @PutMapping("/avatar")
    public UserDtos.UserDto updateAvatar(@Valid @RequestBody UserDtos.UpdateAvatarRequest request) {
        var user = userService.updateAvatar(AuthContext.requireUserId(), request.fileId());
        return UserDtos.UserDto.from(user, userService.levelOf(user));
    }
}
