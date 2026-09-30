package org.shiyi.memo.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.friend.entity.ChatMessage;
import org.shiyi.memo.user.entity.AppUser;

import java.time.LocalDateTime;

public final class FriendDtos {

    private FriendDtos() {
    }

    /** 好友列表项：昵称 + 头像 + 未读聊天数 */
    public record FriendDto(long userId, String nickname, String avatarUrl,
                            long unread, LocalDateTime friendedAt) {

        public static FriendDto from(AppUser user, long unread, LocalDateTime friendedAt) {
            String avatarUrl = user.getAvatarFileId() == null
                    ? null
                    : "/api/files/" + user.getAvatarFileId() + "/thumb?w=160";
            return new FriendDto(user.getId(), user.getNickname(), avatarUrl, unread, friendedAt);
        }
    }

    /** 搜索结果：不给邮箱，够认出人就行 */
    public record UserSearchDto(long userId, String nickname, String avatarUrl) {

        public static UserSearchDto from(AppUser user) {
            String avatarUrl = user.getAvatarFileId() == null
                    ? null
                    : "/api/files/" + user.getAvatarFileId() + "/thumb?w=160";
            return new UserSearchDto(user.getId(), user.getNickname(), avatarUrl);
        }
    }

    public record SendRequestRequest(@NotNull Long toUserId) {
    }

    public record ChatMessageDto(long id, long senderId, boolean mine, String content, LocalDateTime createdAt) {

        public static ChatMessageDto from(ChatMessage message, long viewerId) {
            return new ChatMessageDto(message.getId(), message.getSenderId(),
                    message.getSenderId() == viewerId, message.getContent(), message.getCreatedAt());
        }
    }

    public record SendChatRequest(@NotBlank @Size(max = 2000) String content) {
    }
}
