package org.shiyi.memo.memo.dto;

import jakarta.validation.constraints.Size;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.tag.dto.TagDtos;

import java.time.LocalDateTime;
import java.util.List;

public final class MemoDtos {

    private MemoDtos() {
    }

    public record MemoRequest(@Size(max = 255) String title,
                              @Size(max = 500_000) String contentMd,
                              List<String> tagNames,
                              Boolean pinned) {
    }

    public record MemoSummary(Long id, String title, String excerpt, Boolean pinned,
                              List<TagDtos.TagDto> tags, Long fileCount, String coverUrl,
                              Boolean shareEnabled, String shareToken,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record MemoDetail(Long id, String title, String contentMd, Boolean pinned,
                             List<TagDtos.TagDto> tags, List<FileDtos.FileDto> files,
                             Boolean shareEnabled, String shareToken,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PinRequest(boolean pinned) {
    }

    public record ShareRequest(boolean enabled) {
    }

    public record ShareResponse(boolean enabled, String token) {
    }

    public record PublicMemoDetail(Long id, String title, String contentMd, Long ownerId,
                                   String ownerName, String ownerAvatarUrl, boolean friend,
                                   LocalDateTime updatedAt) {
    }
}
