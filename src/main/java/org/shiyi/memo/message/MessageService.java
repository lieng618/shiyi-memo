package org.shiyi.memo.message;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.message.dto.MessageDtos;
import org.shiyi.memo.message.entity.UserMessage;
import org.shiyi.memo.message.mapper.UserMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_FILE_BLOCKED = "FILE_BLOCKED";
    public static final String TYPE_MEMO_REMOVED = "MEMO_REMOVED";
    public static final String TYPE_FRIEND_REQUEST = "FRIEND_REQUEST";

    private final UserMessageMapper messageMapper;

    public PageResult<MessageDtos.MessageDto> list(long userId, long page, long size) {
        IPage<UserMessage> result = messageMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(UserMessage.class)
                        .eq(UserMessage::getUserId, userId)
                        .orderByDesc(UserMessage::getId));
        return PageResult.of(result.getRecords().stream().map(MessageDtos.MessageDto::from).toList(),
                result.getTotal(), page, size);
    }

    public long unreadCount(long userId) {
        return messageMapper.countUnread(userId);
    }

    @Transactional
    public void markRead(long userId, List<Long> ids) {
        messageMapper.markRead(userId, ids == null || ids.isEmpty() ? null : ids);
    }

    /** 后台发信入口 */
    @Transactional
    public UserMessage send(long userId, String title, String content, String type,
                            Long refMemoId, Long refFileId, String senderName) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(type == null ? TYPE_NOTICE : type);
        message.setRefMemoId(refMemoId);
        message.setRefFileId(refFileId);
        message.setSenderName(senderName == null || senderName.isBlank() ? "管理员" : senderName);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }
}
