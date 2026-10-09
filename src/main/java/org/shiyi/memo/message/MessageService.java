package org.shiyi.memo.message;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.message.dto.MessageDtos;
import org.shiyi.memo.message.entity.UserMessage;
import org.shiyi.memo.message.mapper.UserMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String TYPE_NOTICE = "NOTICE";
    public static final String TYPE_FILE_BLOCKED = "FILE_BLOCKED";
    public static final String TYPE_MEMO_REMOVED = "MEMO_REMOVED";
    public static final String TYPE_WELCOME = "WELCOME";

    private final UserMessageMapper messageMapper;

    public PageResult<MessageDtos.MessageDto> list(long userId, long page, long size) {
        IPage<UserMessage> result = messageMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(UserMessage.class)
                        .eq(UserMessage::getUserId, userId)
                        .ne(UserMessage::getType, "FRIEND_REQUEST")
                        .and(visibleNow())
                        .orderByDesc(UserMessage::getId));
        return PageResult.of(result.getRecords().stream().map(MessageDtos.MessageDto::from).toList(),
                result.getTotal(), page, size);
    }

    public long unreadCount(long userId) {
        return messageMapper.countUnread(userId);
    }

    /** 单条消息详情：只能看自己的，别人的按不存在处理 */
    public MessageDtos.MessageDto detail(long userId, long messageId) {
        UserMessage message = messageMapper.selectById(messageId);
        if (message == null || !message.getUserId().equals(userId)
                || "FRIEND_REQUEST".equals(message.getType()) || isPending(message)) {
            throw ApiException.notFound("消息不存在或已删除");
        }
        return MessageDtos.MessageDto.from(message);
    }

    /** 已送达：没设送达时间（立即发）或已经到点。定时信到点后自己就出现在列表里 */
    static Consumer<LambdaQueryWrapper<UserMessage>> visibleNow() {
        LocalDateTime now = LocalDateTime.now();
        return wrapper -> wrapper.isNull(UserMessage::getSendAt).or().le(UserMessage::getSendAt, now);
    }

    /** 定时信还没到点，对收件人来说就等于不存在 */
    static boolean isPending(UserMessage message) {
        return message.getSendAt() != null && message.getSendAt().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void markRead(long userId, List<Long> ids) {
        messageMapper.markRead(userId, ids == null || ids.isEmpty() ? null : ids);
    }

    /** 系统自动发信（文件屏蔽、笔记删除等），纯文本 */
    @Transactional
    public UserMessage send(long userId, String title, String content, String type,
                            Long refMemoId, Long refFileId, String senderName) {
        UserMessage message = newMessage(userId, title, type, refMemoId, refFileId, senderName);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 后台发富文本通知（单个用户 / 全服信），正文是 wangEditor HTML。
     * 编辑器里引用的是 /api/admin/files/ 前缀（管理员预览走后台鉴权），
     * 落库统一改写成用户侧 /api/files/ 前缀——收信人的附件可见性授权也按这个前缀匹配。
     * content 同步存一份去标签纯文本，给后台列表预览用。
     */
    @Transactional
    public UserMessage sendRich(long userId, String title, String contentHtml, String senderName) {
        return sendRich(userId, title, contentHtml, senderName, null);
    }

    /** 同上，但可以指定送达时间（null = 立即送达） */
    @Transactional
    public UserMessage sendRich(long userId, String title, String contentHtml, String senderName,
                                LocalDateTime sendAt) {
        return insertRich(userId, title, contentHtml, senderName, TYPE_NOTICE, sendAt);
    }

    /** 新用户欢迎信：归到 WELCOME 类型，前台会显示成「欢迎」标签，便于和通知区分 */
    @Transactional
    public UserMessage sendWelcome(long userId, String title, String contentHtml, String senderName) {
        return insertRich(userId, title, contentHtml, senderName, TYPE_WELCOME, null);
    }

    private UserMessage insertRich(long userId, String title, String contentHtml, String senderName,
                                   String type, LocalDateTime sendAt) {
        String html = contentHtml.replace("/api/admin/files/", "/api/files/");
        UserMessage message = newMessage(userId, title, type, null, null, senderName);
        message.setContent(toPlainText(html));
        message.setContentHtml(html);
        message.setSendAt(sendAt);
        messageMapper.insert(message);
        return message;
    }

    private UserMessage newMessage(long userId, String title, String type,
                                   Long refMemoId, Long refFileId, String senderName) {
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setType(type == null ? TYPE_NOTICE : type);
        message.setRefMemoId(refMemoId);
        message.setRefFileId(refFileId);
        message.setSenderName(senderName == null || senderName.isBlank() ? "管理员" : senderName);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    /** HTML 转纯文本摘要：块级标签换成换行、去标签、还原常见实体，超出 2000 字截断 */
    static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|div|h[1-6]|li|blockquote|pre|tr)(\\s[^>]*)?>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }
}
