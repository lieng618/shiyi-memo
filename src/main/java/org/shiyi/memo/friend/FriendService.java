package org.shiyi.memo.friend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.friend.dto.FriendDtos;
import org.shiyi.memo.friend.entity.ChatMessage;
import org.shiyi.memo.friend.entity.Friend;
import org.shiyi.memo.friend.mapper.ChatMessageMapper;
import org.shiyi.memo.friend.mapper.FriendMapper;
import org.shiyi.memo.message.MessageService;
import org.shiyi.memo.message.entity.UserMessage;
import org.shiyi.memo.message.mapper.UserMessageMapper;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 好友：申请沿用站内信（type = FRIEND_REQUEST，ref_user_id = 申请人），
 * 同意后写双向 friend 行；聊天是独立的 chat_message 表，一条消息一行。
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    public static final String REQUEST_PENDING = "PENDING";
    public static final String REQUEST_ACCEPTED = "ACCEPTED";
    public static final String REQUEST_REJECTED = "REJECTED";

    private final FriendMapper friendMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMessageMapper messageMapper;
    private final AppUserMapper userMapper;
    private final MessageService messageService;

    /* ---------------------------------------------------------------- 好友列表 */

    public List<FriendDtos.FriendDto> list(long userId) {
        List<Friend> rows = friendMapper.selectList(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, userId)
                .orderByAsc(Friend::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> friendIds = rows.stream().map(Friend::getFriendId).toList();
        Map<Long, AppUser> users = userMapper.selectBatchIds(friendIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return rows.stream()
                .map(row -> {
                    AppUser user = users.get(row.getFriendId());
                    // 账号没了就不展示，但关系行留着，避免恢复数据时误重建
                    return user == null ? null
                            : FriendDtos.FriendDto.from(user, unreadFrom(user.getId(), userId), row.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void remove(long userId, long friendId) {
        requireFriend(userId, friendId);
        friendMapper.delete(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId));
        friendMapper.delete(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, friendId).eq(Friend::getFriendId, userId));
    }

    /* ---------------------------------------------------------------- 好友申请 */

    /** 按昵称搜人：排除自己、已好友、被禁用的账号 */
    public List<FriendDtos.UserSearchDto> search(long userId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }
        Set<Long> friendIds = friendIdsOf(userId);
        return userMapper.selectList(Wrappers.lambdaQuery(AppUser.class)
                        .eq(AppUser::getStatus, 1)
                        .like(AppUser::getNickname, kw)
                        .ne(AppUser::getId, userId)
                        .orderByAsc(AppUser::getId)
                        .last("limit 10"))
                .stream()
                .filter(user -> !friendIds.contains(user.getId()))
                .map(FriendDtos.UserSearchDto::from)
                .toList();
    }

    @Transactional
    public void sendRequest(long userId, long toUserId) {
        if (userId == toUserId) {
            throw ApiException.badRequest("不能添加自己为好友");
        }
        AppUser target = userMapper.selectById(toUserId);
        if (target == null || !target.isEnabled()) {
            throw ApiException.notFound("用户不存在");
        }
        if (isFriend(userId, toUserId)) {
            throw ApiException.badRequest("你们已经是好友了");
        }
        AppUser me = requireUser(userId);
        if (hasPendingRequest(toUserId, userId)) {
            throw ApiException.badRequest("已发送过好友申请，等待对方处理");
        }
        if (hasPendingRequest(userId, toUserId)) {
            throw ApiException.badRequest("对方已向你发送好友申请，请在站内信里处理");
        }

        UserMessage request = new UserMessage();
        request.setUserId(toUserId);
        request.setTitle("好友申请");
        request.setContent("「" + me.getNickname() + "」（" + me.getEmail() + "）请求加你为好友。");
        request.setType(MessageService.TYPE_FRIEND_REQUEST);
        request.setRefUserId(userId);
        request.setRequestStatus(REQUEST_PENDING);
        request.setSenderName(me.getNickname());
        request.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(request);
    }

    /** 同意或拒绝；messageId 就是那封申请站内信的 id。拒绝保持沉默，不打扰对方 */
    @Transactional
    public void handleRequest(long userId, long messageId, boolean accept) {
        UserMessage message = messageMapper.selectById(messageId);
        if (message == null || !MessageService.TYPE_FRIEND_REQUEST.equals(message.getType())) {
            throw ApiException.notFound("好友申请不存在");
        }
        if (!Objects.equals(message.getUserId(), userId)) {
            throw ApiException.forbidden("只能处理发给自己的好友申请");
        }
        if (!REQUEST_PENDING.equals(message.getRequestStatus())) {
            throw ApiException.badRequest("该申请已处理过了");
        }
        Long requesterId = message.getRefUserId();
        AppUser requester = requesterId == null ? null : userMapper.selectById(requesterId);
        if (requester == null) {
            throw ApiException.badRequest("对方账号不存在");
        }

        message.setRequestStatus(accept ? REQUEST_ACCEPTED : REQUEST_REJECTED);
        messageMapper.updateById(message);
        if (!accept) {
            return;
        }

        if (!isFriend(userId, requesterId)) {
            insertFriendBothWays(userId, requesterId);
        }
        // 通过了才通知对方一声，附上口信让TA来聊天
        AppUser me = requireUser(userId);
        messageService.send(requesterId, "好友申请已通过",
                "「" + me.getNickname() + "」已同意你的好友申请，去好友页和TA聊聊吧。",
                MessageService.TYPE_NOTICE, null, null, me.getNickname());
    }

    /* ---------------------------------------------------------------- 聊天 */

    /**
     * 拉取和某好友的会话。afterId = 0 时返回最近 limit 条（升序），否则只返回更新的消息；
     * 每次拉取顺手把对方发来的标成已读。
     */
    public List<FriendDtos.ChatMessageDto> chat(long userId, long friendId, long afterId, int limit) {
        requireFriend(userId, friendId);
        List<ChatMessage> rows;
        if (afterId > 0) {
            rows = chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                    .gt(ChatMessage::getId, afterId)
                    .and(w -> conversationPair(userId, friendId, w))
                    .orderByAsc(ChatMessage::getId)
                    .last("limit 200"));
        } else {
            rows = chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                    .and(w -> conversationPair(userId, friendId, w))
                    .orderByDesc(ChatMessage::getId)
                    .last("limit " + Math.max(1, Math.min(limit, 200))));
            Collections.reverse(rows);
        }
        // 对方发来的都算已读（开着聊天页呢）
        markReadFrom(friendId, userId);
        return rows.stream().map(row -> FriendDtos.ChatMessageDto.from(row, userId)).toList();
    }

    @Transactional
    public FriendDtos.ChatMessageDto sendChat(long userId, long friendId, String content) {
        requireFriend(userId, friendId);
        AppUser me = requireUser(userId); // 顺带确认自己账号还是正常状态

        ChatMessage message = new ChatMessage();
        message.setSenderId(userId);
        message.setRecipientId(friendId);
        message.setContent(content.trim());
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return FriendDtos.ChatMessageDto.from(message, userId);
    }

    public long unreadTotal(long userId) {
        return chatMessageMapper.selectCount(Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getRecipientId, userId)
                .isNull(ChatMessage::getReadAt));
    }

    /* ---------------------------------------------------------------- 内部工具 */

    private boolean isFriend(long userId, long otherId) {
        return friendMapper.selectCount(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, otherId)) > 0;
    }

    private boolean hasPendingRequest(long recipientId, long requesterId) {
        return messageMapper.selectCount(Wrappers.lambdaQuery(UserMessage.class)
                .eq(UserMessage::getUserId, recipientId)
                .eq(UserMessage::getRefUserId, requesterId)
                .eq(UserMessage::getType, MessageService.TYPE_FRIEND_REQUEST)
                .eq(UserMessage::getRequestStatus, REQUEST_PENDING)) > 0;
    }

    private void insertFriendBothWays(long a, long b) {
        LocalDateTime now = LocalDateTime.now();
        Friend forward = new Friend();
        forward.setUserId(a);
        forward.setFriendId(b);
        forward.setCreatedAt(now);
        Friend backward = new Friend();
        backward.setUserId(b);
        backward.setFriendId(a);
        backward.setCreatedAt(now);
        friendMapper.insert(forward);
        friendMapper.insert(backward);
    }

    private Set<Long> friendIdsOf(long userId) {
        return friendMapper.selectList(Wrappers.lambdaQuery(Friend.class)
                        .eq(Friend::getUserId, userId))
                .stream().map(Friend::getFriendId).collect(Collectors.toSet());
    }

    /** 会话两个方向的消息：AND 优先级高于 OR，正好拼成 (我发TA) OR (TA发我) */
    private void conversationPair(long userId, long friendId, LambdaQueryWrapper<ChatMessage> w) {
        w.eq(ChatMessage::getSenderId, userId).eq(ChatMessage::getRecipientId, friendId)
                .or()
                .eq(ChatMessage::getSenderId, friendId).eq(ChatMessage::getRecipientId, userId);
    }

    private long unreadFrom(long senderId, long recipientId) {
        return chatMessageMapper.selectCount(Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getSenderId, senderId)
                .eq(ChatMessage::getRecipientId, recipientId)
                .isNull(ChatMessage::getReadAt));
    }

    private void markReadFrom(long senderId, long recipientId) {
        chatMessageMapper.update(null, Wrappers.lambdaUpdate(ChatMessage.class)
                .set(ChatMessage::getReadAt, LocalDateTime.now())
                .eq(ChatMessage::getSenderId, senderId)
                .eq(ChatMessage::getRecipientId, recipientId)
                .isNull(ChatMessage::getReadAt));
    }

    private void requireFriend(long userId, long otherId) {
        if (!isFriend(userId, otherId)) {
            throw ApiException.notFound("你们还不是好友");
        }
    }

    private AppUser requireUser(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || !user.isEnabled()) {
            throw ApiException.unauthorized("账号不可用");
        }
        return user;
    }
}
