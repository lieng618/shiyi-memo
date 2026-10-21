package org.shiyi.memo.memo.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.tag.dto.TagDtos;

import java.time.LocalDateTime;
import java.util.List;

public final class MemoDtos {

    private MemoDtos() {
    }

    public record MemoRequest(@Size(max = 255) String title,
                              @Size(max = 500_000) String contentHtml,
                              List<String> tagNames,
                              Boolean pinned,
                              @PositiveOrZero Long expectedVersion) {
    }

    /**
     * 对其他页面只暴露编辑者名称和到期时间，不暴露编辑会话令牌。
     */
    public record EditLeaseInfo(boolean heldByMe, String holderLabel, LocalDateTime expiresAt) {
    }

    public record MemoSummary(Long id, String title, String excerpt, Boolean pinned,
                              List<TagDtos.TagDto> tags, Long fileCount, String coverUrl,
                              Boolean shareEnabled, String shareToken,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record MemoDetail(Long id, String title, String contentHtml, Boolean pinned,
                             List<TagDtos.TagDto> tags, List<FileDtos.FileDto> files,
                             Boolean shareEnabled, String shareToken,
                             Long version, EditLeaseInfo editLease,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PinRequest(boolean pinned) {
    }

    public record ShareRequest(boolean enabled) {
    }

    public record ShareResponse(boolean enabled, String token) {
    }

    public record PublicMemoDetail(Long id, String title, String contentHtml, Long ownerId,
                                   String ownerName, String ownerAvatarUrl, boolean friend,
                                   LocalDateTime updatedAt) {
    }
}
