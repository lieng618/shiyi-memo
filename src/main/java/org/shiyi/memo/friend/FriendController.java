package org.shiyi.memo.friend;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.friend.dto.FriendDtos;
import org.shiyi.memo.security.AuthContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 好友：申请、处理、关系都在好友页完成，同意后才能聊天。
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public List<FriendDtos.FriendDto> list() {
        return friendService.list(AuthContext.requireUserId());
    }

    @DeleteMapping("/{userId}")
    public void remove(@PathVariable long userId) {
        friendService.remove(AuthContext.requireUserId(), userId);
    }

    @GetMapping("/search")
    public List<FriendDtos.UserSearchDto> search(@RequestParam String keyword) {
        return friendService.search(AuthContext.requireUserId(), keyword);
    }

    @PostMapping("/requests")
    public void sendRequest(@Valid @RequestBody FriendDtos.SendRequestRequest request) {
        friendService.sendRequest(AuthContext.requireUserId(), request.toUserId());
    }

    @GetMapping("/requests")
    public FriendDtos.FriendRequestListDto requests() {
        return friendService.requests(AuthContext.requireUserId());
    }

    @GetMapping("/requests/count")
    public FriendDtos.RequestCountDto requestCount() {
        return new FriendDtos.RequestCountDto(friendService.pendingCount(AuthContext.requireUserId()));
    }

    @GetMapping("/summary")
    public FriendDtos.FriendSummaryDto summary() {
        return friendService.summary(AuthContext.requireUserId());
    }

    @GetMapping("/notifications")
    public List<FriendDtos.FriendNotificationDto> notifications() {
        return friendService.notifications(AuthContext.requireUserId());
    }

    @PostMapping("/requests/{requestId}/accept")
    public void accept(@PathVariable long requestId) {
        friendService.handleRequest(AuthContext.requireUserId(), requestId, true);
    }

    @PostMapping("/requests/{requestId}/reject")
    public void reject(@PathVariable long requestId) {
        friendService.handleRequest(AuthContext.requireUserId(), requestId, false);
    }

    @DeleteMapping("/requests/{requestId}")
    public void cancel(@PathVariable long requestId) {
        friendService.cancelRequest(AuthContext.requireUserId(), requestId);
    }

    @GetMapping("/{userId}/chat")
    public List<FriendDtos.ChatMessageDto> chat(@PathVariable long userId,
                                                @RequestParam(defaultValue = "0") long afterId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return friendService.chat(AuthContext.requireUserId(), userId, afterId, limit);
    }

    /** 查聊天内容：type = text 按关键词搜文字，type = memo 只看分享的笔记 */
    @GetMapping("/{userId}/chat/search")
    public List<FriendDtos.ChatMessageDto> searchChat(@PathVariable long userId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "text") String type) {
        return friendService.searchChat(AuthContext.requireUserId(), userId, keyword,
                "memo".equalsIgnoreCase(type));
    }

    @DeleteMapping("/{userId}/chat")
    public void clearChat(@PathVariable long userId) {
        friendService.clearChat(AuthContext.requireUserId(), userId);
    }

    @PostMapping("/{userId}/chat")
    public FriendDtos.ChatMessageDto sendChat(@PathVariable long userId,
                                              @Valid @RequestBody FriendDtos.SendChatRequest request) {
        return friendService.sendChat(AuthContext.requireUserId(), userId, request.content());
    }
}
