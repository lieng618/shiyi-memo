package org.shiyi.memo.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.security.AuthContext;
import org.shiyi.memo.user.dto.UserDtos;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FileService fileService;

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

    /** 好友 / 搜索列表读取用户头像；头像文件被屏蔽或不存在时返回 404。 */
    @GetMapping("/{userId}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable long userId,
                                           @RequestParam(defaultValue = "160") Integer w) {
        FileAsset asset = fileService.publicAvatar(userService.requireEnabled(userId));
        if (asset == null) {
            throw ApiException.notFound("头像不存在");
        }
        int width = Math.max(32, Math.min(w, 1024));
        Path thumbnail = fileService.thumbnailOf(asset, width);
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();
        if (thumbnail == null) {
            return ResponseEntity.ok()
                    .contentType(mediaType(asset))
                    .cacheControl(cache)
                    .body(new FileSystemResource(fileService.pathOf(asset)));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(cache)
                .body(new FileSystemResource(thumbnail));
    }

    private MediaType mediaType(FileAsset asset) {
        try {
            return MediaType.parseMediaType(asset.getMime());
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
