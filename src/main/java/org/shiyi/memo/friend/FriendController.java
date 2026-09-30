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
 * 好友：申请走站内信，同意后才能聊天。
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

    /** messageId = 那封申请站内信的 id */
    @PostMapping("/requests/{messageId}/accept")
    public void accept(@PathVariable long messageId) {
        friendService.handleRequest(AuthContext.requireUserId(), messageId, true);
    }

    @PostMapping("/requests/{messageId}/reject")
    public void reject(@PathVariable long messageId) {
        friendService.handleRequest(AuthContext.requireUserId(), messageId, false);
    }

    @GetMapping("/{userId}/chat")
    public List<FriendDtos.ChatMessageDto> chat(@PathVariable long userId,
                                                @RequestParam(defaultValue = "0") long afterId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return friendService.chat(AuthContext.requireUserId(), userId, afterId, limit);
    }

    @PostMapping("/{userId}/chat")
    public FriendDtos.ChatMessageDto sendChat(@PathVariable long userId,
                                              @Valid @RequestBody FriendDtos.SendChatRequest request) {
        return friendService.sendChat(AuthContext.requireUserId(), userId, request.content());
    }
}
