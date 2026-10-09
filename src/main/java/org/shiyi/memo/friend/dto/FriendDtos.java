package org.shiyi.memo.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.friend.entity.ChatMessage;
import org.shiyi.memo.friend.entity.FriendRequest;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.dto.UserDtos;

import java.time.LocalDateTime;
import java.util.List;

public final class FriendDtos {

    private FriendDtos() {
    }

    /** 好友列表项：昵称 + 头像 + 未读聊天数 */
    public record FriendDto(long userId, String nickname, String avatarUrl,
                            long unread, LocalDateTime friendedAt) {

        public static FriendDto from(AppUser user, long unread, LocalDateTime friendedAt) {
            return new FriendDto(user.getId(), user.getNickname(), UserDtos.avatarUrl(user), unread, friendedAt);
        }
    }

    /** 搜索结果：不给邮箱，够认出人就行 */
    public record UserSearchDto(long userId, String nickname, String avatarUrl) {

        public static UserSearchDto from(AppUser user) {
            return new UserSearchDto(user.getId(), user.getNickname(), UserDtos.avatarUrl(user));
        }
    }

    public record SendRequestRequest(@NotNull Long toUserId) {
    }

    /** 邀请链接落地页看到的信息：只给昵称，够认出是谁就行 */
    public record InviteCardDto(long userId, String nickname) {

        public static InviteCardDto from(AppUser user) {
            return new InviteCardDto(user.getId(), user.getNickname());
        }
    }

    /** 好友页申请列表：incoming = 收到的，outgoing = 我发出的 */
    public record FriendRequestListDto(List<FriendRequestDto> incoming,
                                       List<FriendRequestDto> outgoing) {
    }

    public record FriendRequestDto(long id, long userId, String nickname, String avatarUrl,
                                   String status, LocalDateTime createdAt) {

        public static FriendRequestDto from(FriendRequest request, AppUser other) {
            return new FriendRequestDto(request.getId(), other.getId(), other.getNickname(),
                    UserDtos.avatarUrl(other), request.getStatus(), request.getCreatedAt());
        }
    }

    public record RequestCountDto(long count) {
    }

    public record FriendSummaryDto(long pendingRequests, long unreadChats) {
    }

    public record FriendNotificationDto(String type, long userId, String nickname, String avatarUrl,
                                        String content, long unread, LocalDateTime createdAt) {
    }

    public record ChatMessageDto(long id, long senderId, boolean mine, String content, boolean failed,
                                 LocalDateTime createdAt) {

        public static ChatMessageDto from(ChatMessage message, long viewerId) {
            return new ChatMessageDto(message.getId(), message.getSenderId(),
                    message.getSenderId() == viewerId, message.getContent(),
                    !Boolean.TRUE.equals(message.getDeliverable()), message.getCreatedAt());
        }
    }

    public record SendChatRequest(@NotBlank @Size(max = 2000) String content) {
    }
}
