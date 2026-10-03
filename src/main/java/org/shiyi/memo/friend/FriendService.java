package org.shiyi.memo.friend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.friend.dto.FriendDtos;
import org.shiyi.memo.friend.entity.ChatClear;
import org.shiyi.memo.friend.entity.ChatMessage;
import org.shiyi.memo.friend.entity.Friend;
import org.shiyi.memo.friend.entity.FriendRequest;
import org.shiyi.memo.friend.mapper.ChatClearMapper;
import org.shiyi.memo.friend.mapper.ChatMessageMapper;
import org.shiyi.memo.friend.mapper.FriendMapper;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.friend.mapper.FriendRequestMapper;
import org.shiyi.memo.realtime.RealtimeEventService;
import org.shiyi.memo.user.dto.UserDtos;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 好友：申请、处理、关系都独立管理；聊天是独立的 chat_message 表，一条消息一行。
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    public static final String REQUEST_PENDING = "PENDING";
    public static final String REQUEST_ACCEPTED = "ACCEPTED";
    public static final String REQUEST_REJECTED = "REJECTED";
    public static final String REQUEST_CANCELED = "CANCELED";

    private final FriendMapper friendMapper;
    private final FriendRequestMapper requestMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatClearMapper chatClearMapper;
    private final AppUserMapper userMapper;
    private final FileService fileService;
    private final RealtimeEventService realtimeEventService;

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
        // 单向删除：只删自己这一行。对方的列表和历史都留着（微信那种），
        // 但对方再发消息时会因为缺了反向关系被拦下
        friendMapper.delete(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, userId).eq(Friend::getFriendId, friendId));
        realtimeEventService.sendAfterCommit(userId, "friends", "refresh");
        realtimeEventService.sendAfterCommit(friendId, "friends", "refresh");
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
        // 只有双方都还在对方列表里才算好友；一方单删过，另一方可以重新申请
        if (isFriend(userId, toUserId) && isFriend(toUserId, userId)) {
            throw ApiException.badRequest("你们已经是好友了");
        }
        requireUser(userId);
        if (hasPendingRequest(userId, toUserId)) {
            throw ApiException.badRequest("已发送过好友申请，等待对方处理");
        }
        if (hasPendingRequest(toUserId, userId)) {
            throw ApiException.badRequest("对方已向你发送好友申请，请在好友页处理");
        }

        FriendRequest request = new FriendRequest();
        request.setFromUserId(userId);
        request.setToUserId(toUserId);
        request.setStatus(REQUEST_PENDING);
        request.setCreatedAt(LocalDateTime.now());
        requestMapper.insert(request);
        realtimeEventService.sendAfterCommit(toUserId, "friends", "refresh");
        realtimeEventService.sendAfterCommit(userId, "friends", "refresh");
    }

    public FriendDtos.FriendRequestListDto requests(long userId) {
        List<FriendRequest> incoming = requestMapper.selectList(Wrappers.lambdaQuery(FriendRequest.class)
                .eq(FriendRequest::getToUserId, userId)
                .orderByDesc(FriendRequest::getId)
                .last("limit 50"));
        List<FriendRequest> outgoing = requestMapper.selectList(Wrappers.lambdaQuery(FriendRequest.class)
                .eq(FriendRequest::getFromUserId, userId)
                .orderByDesc(FriendRequest::getId)
                .last("limit 50"));

        List<Long> userIds = incoming.stream().map(FriendRequest::getFromUserId)
                .collect(Collectors.toList());
        userIds.addAll(outgoing.stream().map(FriendRequest::getToUserId).toList());
        Map<Long, AppUser> users = usersOf(userIds);

        return new FriendDtos.FriendRequestListDto(
                incoming.stream().map(request -> {
                    AppUser other = users.get(request.getFromUserId());
                    return other == null ? null : FriendDtos.FriendRequestDto.from(request, other);
                }).filter(Objects::nonNull).toList(),
                outgoing.stream().map(request -> {
                    AppUser other = users.get(request.getToUserId());
                    return other == null ? null : FriendDtos.FriendRequestDto.from(request, other);
                }).filter(Objects::nonNull).toList());
    }

    public long pendingCount(long userId) {
        return requestMapper.selectCount(Wrappers.lambdaQuery(FriendRequest.class)
                .eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, REQUEST_PENDING));
    }

    public FriendDtos.FriendSummaryDto summary(long userId) {
        return new FriendDtos.FriendSummaryDto(pendingCount(userId), unreadTotal(userId));
    }

    /** 顶部好友按钮悬停预览：未读聊天 + 待处理好友申请。 */
    public List<FriendDtos.FriendNotificationDto> notifications(long userId) {
        List<ChatMessage> chatRows = chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getRecipientId, userId)
                .isNull(ChatMessage::getReadAt)
                .eq(ChatMessage::getDeliverable, true)
                .orderByDesc(ChatMessage::getId)
                .last("limit 100"));
        Map<Long, ChatMessage> latest = new LinkedHashMap<>();
        Map<Long, Long> unread = new LinkedHashMap<>();
        for (ChatMessage row : chatRows) {
            latest.putIfAbsent(row.getSenderId(), row);
            unread.merge(row.getSenderId(), 1L, Long::sum);
        }
        List<FriendRequest> requestRows = requestMapper.selectList(Wrappers.lambdaQuery(FriendRequest.class)
                .eq(FriendRequest::getToUserId, userId)
                .eq(FriendRequest::getStatus, REQUEST_PENDING)
                .orderByDesc(FriendRequest::getId)
                .last("limit 50"));

        List<Long> userIds = new ArrayList<>(latest.keySet());
        userIds.addAll(requestRows.stream().map(FriendRequest::getFromUserId).toList());
        Map<Long, AppUser> users = usersOf(userIds);
        List<FriendDtos.FriendNotificationDto> result = new ArrayList<>();

        latest.forEach((senderId, message) -> {
            AppUser sender = users.get(senderId);
            if (sender != null) {
                result.add(new FriendDtos.FriendNotificationDto("CHAT", sender.getId(), sender.getNickname(),
                        UserDtos.avatarUrl(sender), preview(message.getContent()),
                        unread.getOrDefault(senderId, 0L), message.getCreatedAt()));
            }
        });
        for (FriendRequest request : requestRows) {
            AppUser applicant = users.get(request.getFromUserId());
            if (applicant != null) {
                result.add(new FriendDtos.FriendNotificationDto("FRIEND_REQUEST", applicant.getId(),
                        applicant.getNickname(), UserDtos.avatarUrl(applicant),
                        "请求加你为好友", 1, request.getCreatedAt()));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(FriendDtos.FriendNotificationDto::createdAt).reversed())
                .limit(20)
                .toList();
    }

    /** 同意或拒绝；拒绝保持沉默，不打扰对方。 */
    @Transactional
    public void handleRequest(long userId, long requestId, boolean accept) {
        FriendRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw ApiException.notFound("好友申请不存在");
        }
        if (!Objects.equals(request.getToUserId(), userId)) {
            throw ApiException.forbidden("只能处理发给自己的好友申请");
        }
        if (!REQUEST_PENDING.equals(request.getStatus())) {
            throw ApiException.badRequest("该申请已处理过了");
        }
        Long requesterId = request.getFromUserId();
        AppUser requester = requesterId == null ? null : userMapper.selectById(requesterId);
        if (requester == null) {
            throw ApiException.badRequest("对方账号不存在");
        }

        request.setStatus(accept ? REQUEST_ACCEPTED : REQUEST_REJECTED);
        request.setHandledAt(LocalDateTime.now());
        requestMapper.updateById(request);
        if (!accept) {
            return;
        }

        // 单删过的关系在这里恢复：两个方向谁缺补谁，已有的一行保留原样
        insertFriendIfAbsent(userId, requesterId);
        insertFriendIfAbsent(requesterId, userId);
        realtimeEventService.sendAfterCommit(userId, "friends", "refresh");
        realtimeEventService.sendAfterCommit(requesterId, "friends", "refresh");
    }

    @Transactional
    public void cancelRequest(long userId, long requestId) {
        FriendRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw ApiException.notFound("好友申请不存在");
        }
        if (!Objects.equals(request.getFromUserId(), userId)) {
            throw ApiException.forbidden("只能撤回自己发出的好友申请");
        }
        if (!REQUEST_PENDING.equals(request.getStatus())) {
            throw ApiException.badRequest("该申请已处理过了");
        }
        request.setStatus(REQUEST_CANCELED);
        request.setHandledAt(LocalDateTime.now());
        requestMapper.updateById(request);
        realtimeEventService.sendAfterCommit(userId, "friends", "refresh");
        realtimeEventService.sendAfterCommit(request.getToUserId(), "friends", "refresh");
    }

    /* ---------------------------------------------------------------- 聊天 */

    /**
     * 拉取和某好友的会话。afterId = 0 时返回最近 limit 条（升序），否则只返回更新的消息；
     * 每次拉取顺手把对方发来的标成已读。自己清空过的聊天记录不再返回。
     */
    public List<FriendDtos.ChatMessageDto> chat(long userId, long friendId, long afterId, int limit) {
        requireFriend(userId, friendId);
        LocalDateTime cleared = clearedAt(userId, friendId);
        List<ChatMessage> rows;
        if (afterId > 0) {
            rows = chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                    .gt(ChatMessage::getId, afterId)
                    .and(w -> conversationPair(userId, friendId, w))
                    .gt(cleared != null, ChatMessage::getCreatedAt, cleared)
                    .orderByAsc(ChatMessage::getId)
                    .last("limit 200"));
        } else {
            rows = chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                    .and(w -> conversationPair(userId, friendId, w))
                    .gt(cleared != null, ChatMessage::getCreatedAt, cleared)
                    .orderByDesc(ChatMessage::getId)
                    .last("limit " + Math.max(1, Math.min(limit, 200))));
            Collections.reverse(rows);
        }
        // 对方发来的都算已读（开着聊天页呢）
        markReadFrom(friendId, userId);
        return rows.stream().map(row -> FriendDtos.ChatMessageDto.from(row, userId)).toList();
    }

    /**
     * 查聊天内容：memoOnly = true 时只看分享的笔记，否则按关键词搜纯文字消息
     * （表情、笔记分享和文件消息的原始内容是一串标记，按文字搜没有意义，直接排除）。
     */
    public List<FriendDtos.ChatMessageDto> searchChat(long userId, long friendId, String keyword, boolean memoOnly) {
        requireFriend(userId, friendId);
        String kw = keyword == null ? "" : keyword.trim();
        if (!memoOnly && kw.isEmpty()) {
            return List.of();
        }
        LocalDateTime cleared = clearedAt(userId, friendId);
        return chatMessageMapper.selectList(Wrappers.lambdaQuery(ChatMessage.class)
                        .and(w -> conversationPair(userId, friendId, w))
                        .gt(cleared != null, ChatMessage::getCreatedAt, cleared)
                        .likeRight(memoOnly, ChatMessage::getContent, MEMO_SHARE_PREFIX)
                        .like(!memoOnly, ChatMessage::getContent, escapeLike(kw))
                        .notLikeRight(!memoOnly, ChatMessage::getContent, SMOJI_PREFIX)
                        .notLikeRight(!memoOnly, ChatMessage::getContent, MEMO_SHARE_PREFIX)
                        .notLikeRight(!memoOnly, ChatMessage::getContent, FILE_PREFIX)
                        .orderByDesc(ChatMessage::getId)
                        .last("limit 50"))
                .stream()
                .map(row -> FriendDtos.ChatMessageDto.from(row, userId))
                .toList();
    }

    /** 清空聊天记录：只影响自己这一侧，顺便把这边的未读一并清掉 */
    @Transactional
    public void clearChat(long userId, long friendId) {
        requireFriend(userId, friendId);
        LocalDateTime now = LocalDateTime.now();
        ChatClear marker = chatClearMapper.selectOne(Wrappers.lambdaQuery(ChatClear.class)
                .eq(ChatClear::getUserId, userId)
                .eq(ChatClear::getFriendId, friendId));
        if (marker == null) {
            marker = new ChatClear();
            marker.setUserId(userId);
            marker.setFriendId(friendId);
            marker.setClearedAt(now);
            chatClearMapper.insert(marker);
        } else {
            marker.setClearedAt(now);
            chatClearMapper.updateById(marker);
        }
        markReadFrom(friendId, userId);
    }

    @Transactional
    public FriendDtos.ChatMessageDto sendChat(long userId, long friendId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith(FILE_PREFIX)) {
            requireValidFileMarker(userId, trimmed);
        }
        // 自己这边删了对方就不能发；对方删了我还是能发，但这条只留给自己看
        requireFriend(userId, friendId);
        AppUser me = requireUser(userId); // 顺带确认自己账号还是正常状态
        boolean deliverable = isFriend(friendId, userId);

        ChatMessage message = new ChatMessage();
        message.setSenderId(userId);
        message.setRecipientId(friendId);
        message.setContent(trimmed);
        message.setDeliverable(deliverable);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        FriendDtos.ChatMessageDto dto = FriendDtos.ChatMessageDto.from(message, userId);
        if (deliverable) {
            realtimeEventService.sendAfterCommit(friendId, "chat", dto);
        }
        return dto;
    }

    /**
     * 重发没送出去的消息：关系恢复了就真正发一条出去，把那条失败记录换掉
     * （对方是按 id 增量拉新消息的，沿用旧 id 会漏掉）；还是发不出去就报错，
     * 让前端保持红叹号
     */
    @Transactional
    public FriendDtos.ChatMessageDto resendChat(long userId, long friendId, long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null || !Objects.equals(message.getSenderId(), userId)
                || !Objects.equals(message.getRecipientId(), friendId)) {
            throw ApiException.notFound("消息不存在");
        }
        requireFriend(userId, friendId);

        if (Boolean.TRUE.equals(message.getDeliverable())) {
            // 本来就发出去了，直接回原样，避免重复推给对面
            return FriendDtos.ChatMessageDto.from(message, userId);
        }
        if (!isFriend(friendId, userId)) {
            throw ApiException.forbidden("对方不是你的好友");
        }

        ChatMessage fresh = new ChatMessage();
        fresh.setSenderId(userId);
        fresh.setRecipientId(friendId);
        fresh.setContent(message.getContent());
        fresh.setDeliverable(true);
        fresh.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(fresh);
        chatMessageMapper.deleteById(message.getId());

        FriendDtos.ChatMessageDto dto = FriendDtos.ChatMessageDto.from(fresh, userId);
        realtimeEventService.sendAfterCommit(friendId, "chat", dto);
        return dto;
    }

    public long unreadTotal(long userId) {
        return chatMessageMapper.selectCount(Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getRecipientId, userId)
                .eq(ChatMessage::getDeliverable, true)
                .isNull(ChatMessage::getReadAt));
    }

    /* ---------------------------------------------------------------- 内部工具 */

    /** 聊天气泡里表情、笔记分享和文件的原文标记，和前端约定一致 */
    private static final String SMOJI_PREFIX = "[[smoji:";
    private static final String MEMO_SHARE_PREFIX = "[[memo-share:";
    private static final String FILE_PREFIX = "[[file:";
    /** [[file:{fileId}:{kind}:{size}:{encodeURIComponent(文件名)}]] */
    private static final Pattern FILE_MARKER = Pattern.compile(
            "^\\[\\[file:(\\d+):(image|video|audio|other):(\\d+):([^\\]]+)\\]\\]$");

    /** 文件消息：标记必须完整，而且文件确实存在、属于发送者、没被屏蔽 */
    private void requireValidFileMarker(long userId, String content) {
        Matcher matcher = FILE_MARKER.matcher(content);
        if (!matcher.matches()) {
            throw ApiException.badRequest("文件消息格式不正确");
        }
        FileAsset asset = fileService.requireOwned(userId, Long.parseLong(matcher.group(1)));
        if (Boolean.TRUE.equals(asset.getBlocked())) {
            throw ApiException.forbidden("该文件已被管理员屏蔽");
        }
    }

    public boolean isFriend(long userId, long otherId) {
        return friendMapper.selectCount(Wrappers.lambdaQuery(Friend.class)
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, otherId)) > 0;
    }

    private boolean hasPendingRequest(long fromUserId, long toUserId) {
        return requestMapper.selectCount(Wrappers.lambdaQuery(FriendRequest.class)
                .eq(FriendRequest::getFromUserId, fromUserId)
                .eq(FriendRequest::getToUserId, toUserId)
                .eq(FriendRequest::getStatus, REQUEST_PENDING)) > 0;
    }

    /** 补一行好友关系，已经有就跳过（单删后重新加好友会走到这里） */
    private void insertFriendIfAbsent(long userId, long friendId) {
        if (isFriend(userId, friendId)) {
            return;
        }
        Friend row = new Friend();
        row.setUserId(userId);
        row.setFriendId(friendId);
        row.setCreatedAt(LocalDateTime.now());
        friendMapper.insert(row);
    }

    private Set<Long> friendIdsOf(long userId) {
        return friendMapper.selectList(Wrappers.lambdaQuery(Friend.class)
                        .eq(Friend::getUserId, userId))
                .stream().map(Friend::getFriendId).collect(Collectors.toSet());
    }

    private Map<Long, AppUser> usersOf(List<Long> userIds) {
        Map<Long, AppUser> users = new LinkedHashMap<>();
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return users;
        }
        for (AppUser user : userMapper.selectBatchIds(distinct)) {
            users.put(user.getId(), user);
        }
        return users;
    }

    /** 会话两个方向的消息：AND 优先级高于 OR，正好拼成 (我发TA) OR (TA发我) */
    private void conversationPair(long userId, long friendId, LambdaQueryWrapper<ChatMessage> w) {
        w.eq(ChatMessage::getSenderId, userId).eq(ChatMessage::getRecipientId, friendId)
                .or()
                .eq(ChatMessage::getSenderId, friendId).eq(ChatMessage::getRecipientId, userId)
                // 我自己发出去的没送达消息要留着，对方发不进来的那种本来也查不到
                .eq(ChatMessage::getDeliverable, true);
    }

    private long unreadFrom(long senderId, long recipientId) {
        return chatMessageMapper.selectCount(Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getSenderId, senderId)
                .eq(ChatMessage::getRecipientId, recipientId)
                .eq(ChatMessage::getDeliverable, true)
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

    /** 自己对这个会话的清空时间点，没清空过就是 null */
    private LocalDateTime clearedAt(long userId, long friendId) {
        ChatClear marker = chatClearMapper.selectOne(Wrappers.lambdaQuery(ChatClear.class)
                .eq(ChatClear::getUserId, userId)
                .eq(ChatClear::getFriendId, friendId));
        return marker == null ? null : marker.getClearedAt();
    }

    private AppUser requireUser(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || !user.isEnabled()) {
            throw ApiException.unauthorized("账号不可用");
        }
        return user;
    }

    private static String preview(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith(SMOJI_PREFIX)) {
            return "[表情]";
        }
        if (value.startsWith(MEMO_SHARE_PREFIX)) {
            return "[分享的笔记]";
        }
        if (value.startsWith(FILE_PREFIX)) {
            return filePreviewLabel(value);
        }
        return value.length() > 80 ? value.substring(0, 80) + "…" : value;
    }

    private static String filePreviewLabel(String content) {
        Matcher matcher = FILE_MARKER.matcher(content);
        if (!matcher.matches()) {
            return "[文件]";
        }
        return switch (matcher.group(2)) {
            case "image" -> "[图片]";
            case "video" -> "[视频]";
            case "audio" -> "[语音]";
            default -> "[文件]";
        };
    }

    /** 关键词里的 % 和 _ 是 like 的通配符，搜索时要当成普通字符 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
