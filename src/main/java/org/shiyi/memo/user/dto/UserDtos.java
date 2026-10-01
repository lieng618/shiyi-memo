package org.shiyi.memo.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.entity.UserLevel;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    /** 用户头像统一走按用户读取的接口，避免暴露附件归属权限问题。 */
    public static String avatarUrl(AppUser user) {
        if (user == null || user.getAvatarFileId() == null) {
            return null;
        }
        return "/api/user/" + user.getId() + "/avatar?w=160&v=" + user.getAvatarFileId();
    }

    public record LevelDto(Long id, String code, String name, Long maxStorage,
                           String description, Integer sort, Boolean isDefault, Long userCount) {

        public static LevelDto from(UserLevel level) {
            return from(level, null);
        }

        public static LevelDto from(UserLevel level, Long userCount) {
            return new LevelDto(level.getId(), level.getCode(), level.getName(),
                    level.getMaxStorage(), level.getDescription(), level.getSort(), level.getIsDefault(), userCount);
        }
    }

    public record UserDto(Long id, String email, String nickname, String avatarUrl, Boolean hasPassword, Integer status,
                          LevelDto level, Long storageUsed, Long maxStorage,
                          LocalDateTime lastLoginAt, LocalDateTime createdAt) {

        public static UserDto from(AppUser user, UserLevel level) {
            boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
            return new UserDto(user.getId(), user.getEmail(), user.getNickname(), UserDtos.avatarUrl(user),
                    hasPassword, user.getStatus(),
                    level == null ? null : LevelDto.from(level),
                    user.getStorageUsed(),
                    level == null ? null : level.getMaxStorage(),
                    user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record QuotaDto(Long levelId, String levelName, Long maxStorage, Long used,
                           Long remainingFiles, Integer usedPercent) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 64) String nickname) {
    }

    public record UpdateLevelRequest(@NotNull Long levelId) {
    }

    public record UpdateStatusRequest(@NotNull Integer status) {
    }

    public record LevelRequest(@Size(max = 32) String code,
                               @NotBlank @Size(max = 64) String name,
                               @NotNull @Min(1) Long maxStorage,
                               @Size(max = 255) String description,
                               Integer sort,
                               Boolean isDefault) {
    }

    /** fileId = null 表示恢复默认头像。 */
    public record UpdateAvatarRequest(Long fileId) {
    }

    /** 已登录用户直接设置新密码，不再重复校验邮箱验证码。 */
    public record UpdatePasswordRequest(@NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
