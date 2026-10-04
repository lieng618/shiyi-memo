package org.shiyi.memo.message;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.message.dto.MessageDtos;
import org.shiyi.memo.security.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户的站内信：只能看，不能删。
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public PageResult<MessageDtos.MessageDto> list(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return messageService.list(AuthContext.requireUserId(),
                Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    /** 单条消息详情（详情页用；是否已读由前端在进入详情后另行标记） */
    @GetMapping("/{id}")
    public MessageDtos.MessageDto detail(@PathVariable long id) {
        return messageService.detail(AuthContext.requireUserId(), id);
    }

    @GetMapping("/unread-count")
    public MessageDtos.UnreadDto unreadCount() {
        return new MessageDtos.UnreadDto(messageService.unreadCount(AuthContext.requireUserId()));
    }

    /** 打开站内信页面时调用；不传 ids 表示全部标为已读 */
    @PostMapping("/read")
    public MessageDtos.UnreadDto markRead(@RequestBody(required = false) MessageDtos.ReadRequest request) {
        long userId = AuthContext.requireUserId();
        messageService.markRead(userId, request == null ? null : request.ids());
        return new MessageDtos.UnreadDto(messageService.unreadCount(userId));
    }
}
