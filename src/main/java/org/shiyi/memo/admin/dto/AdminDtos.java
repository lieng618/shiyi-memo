package org.shiyi.memo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.file.dto.FileDtos;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 128) String password) {
    }

    public record LoginResponse(String username, long expiresInSeconds) {
    }

    public record AdminUserDto(Long id, String email, String nickname, String avatarUrl, Integer status,
                               Long levelId, String levelName, Long maxStorage, Long storageUsed,
                               Long memoCount, Long fileCount, Long blockedFileCount, Long messageCount,
                               LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record UpdateUserRequest(@Size(max = 64) String nickname, Long levelId, Integer status) {
    }

    public record AdminMemoDto(Long id, Long userId, String userEmail, String userNickname, String title,
                               String excerpt, Boolean pinned, Long fileCount,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record AdminMemoDetail(Long id, Long userId, String userEmail, String userNickname, String title,
                                  String contentHtml, List<FileDtos.FileDto> files,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record AdminFileDto(Long id, Long userId, String userEmail, Long memoId, String originalName,
                               String kind, String mime, Long size, Boolean blocked, String blockedReason,
                               LocalDateTime blockedAt, LocalDateTime createdAt) {
    }

    public record BlockFileRequest(@Size(max = 255) String reason) {
    }

    /** 给单个用户发站内信；sendAt 为 null 表示立即送达，填未来时间就是定时送达 */
    public record SendMessageRequest(@NotNull Long userId,
                                     @NotBlank @Size(max = 128) String title,
                                     @NotBlank @Size(max = 60000) String contentHtml,
                                     LocalDateTime sendAt) {
    }

    /** 全服信：sendAt 同单发，null 表示立即送达 */
    public record BroadcastMessageRequest(@NotBlank @Size(max = 128) String title,
                                          @NotBlank @Size(max = 60000) String contentHtml,
                                          LocalDateTime sendAt) {
    }

    public record AdminMessageDto(Long id, Long userId, String userEmail, String userNickname,
                                  String title, String content, String type, String senderName,
                                  LocalDateTime sendAt, LocalDateTime readAt, LocalDateTime createdAt) {
    }

    // ---------------- 欢迎信 ----------------

    /** 欢迎信的标题和正文允许先存草稿（关闭状态），所以只在服务端校验「开启时必须填全」 */
    public record WelcomeLetterRequest(@NotNull Boolean enabled,
                                       @Size(max = 128) String title,
                                       @Size(max = 60000) String contentHtml) {
    }

    public record WelcomeLetterDto(boolean enabled, String title, String contentHtml,
                                   String updatedBy, LocalDateTime updatedAt) {
    }

    // ---------------- 验证码 ----------------

    /**
     * 邮箱验证码的只读视图，用来排查「收不到验证码」。
     * status：VALID 有效 / EXPIRED 已过期 / USED 已使用 / BLOCKED 错误次数过多。
     * remainingSeconds 只在还没过期时为非空，前端据此做倒计时。
     */
    public record AdminEmailCodeDto(Long id, String email, String code, String status,
                                    LocalDateTime expiresAt, LocalDateTime usedAt,
                                    Integer attempts, String ip, LocalDateTime createdAt,
                                    Long remainingSeconds) {
    }

    public record StatsDto(long userCount, long memoCount, long fileCount, long blockedFileCount,
                           long storageUsed, long messageCount, long unreadMessageCount) {
    }
}
