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
            String avatarUrl = user.getAvatarFileId() == null
                    ? null
                    : "/api/files/" + user.getAvatarFileId() + "/thumb?w=160";
            boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
            return new UserDto(user.getId(), user.getEmail(), user.getNickname(), avatarUrl,
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

    public record UpdateAvatarRequest(@NotNull Long fileId) {
    }

    /**
     * 设置 / 修改登录密码。
     * 已有密码时 currentPassword 与 code 二选一；没设过密码时必须给 code（邮箱验证码）。
     */
    public record UpdatePasswordRequest(@Size(max = 72) String currentPassword,
                                        @Size(max = 16) String code,
                                        @NotBlank @Size(max = 72) String newPassword) {
    }
}
