package org.shiyi.memo.message.dto;

import org.shiyi.memo.message.entity.UserMessage;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record MessageDto(Long id, String title, String content, String type,
                             Long refMemoId, Long refFileId, String senderName,
                             LocalDateTime readAt, LocalDateTime createdAt) {

        public static MessageDto from(UserMessage message) {
            return new MessageDto(message.getId(), message.getTitle(), message.getContent(), message.getType(),
                    message.getRefMemoId(), message.getRefFileId(), message.getSenderName(),
                    message.getReadAt(), message.getCreatedAt());
        }
    }

    public record UnreadDto(long count) {
    }

    /** ids 为空 = 全部标为已读 */
    public record ReadRequest(List<Long> ids) {
    }
}
