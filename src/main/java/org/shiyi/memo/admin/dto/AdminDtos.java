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
                                  String contentMd, List<FileDtos.FileDto> files,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record AdminFileDto(Long id, Long userId, String userEmail, Long memoId, String originalName,
                               String kind, String mime, Long size, Boolean blocked, String blockedReason,
                               LocalDateTime blockedAt, LocalDateTime createdAt) {
    }

    public record BlockFileRequest(@Size(max = 255) String reason) {
    }

    public record SendMessageRequest(@NotNull Long userId,
                                     @NotBlank @Size(max = 128) String title,
                                     @NotBlank @Size(max = 2000) String content) {
    }

    public record AdminMessageDto(Long id, Long userId, String userEmail, String userNickname,
                                  String title, String content, String type, String senderName,
                                  LocalDateTime readAt, LocalDateTime createdAt) {
    }

    public record StatsDto(long userCount, long memoCount, long fileCount, long blockedFileCount,
                           long storageUsed, long messageCount, long unreadMessageCount) {
    }
}
