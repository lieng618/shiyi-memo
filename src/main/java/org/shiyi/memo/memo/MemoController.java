package org.shiyi.memo.memo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.memo.dto.MemoDtos;
import org.shiyi.memo.security.AuthContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    /**
     * @param keyword 标题 + 正文模糊搜索
     * @param tagIds  按标签过滤，多个标签取并集
     */
    @GetMapping
    public PageResult<MemoDtos.MemoSummary> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) List<Long> tagIds,
                                                 @RequestParam(required = false) Boolean pinned,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return memoService.list(AuthContext.requireUserId(), keyword, tagIds, pinned,
                Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/{id}")
    public MemoDtos.MemoDetail detail(
            @PathVariable long id,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId) {
        return memoService.detail(AuthContext.requireUserId(), id, sessionId);
    }

    @PostMapping
    public MemoDtos.MemoDetail create(
            @Valid @RequestBody MemoDtos.MemoRequest request,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId,
            @RequestHeader(value = "X-Edit-Client", required = false) String clientLabel) {
        return memoService.create(AuthContext.requireUserId(), request, sessionId, clientLabel);
    }

    @PutMapping("/{id}")
    public MemoDtos.MemoDetail update(
            @PathVariable long id,
            @Valid @RequestBody MemoDtos.MemoRequest request,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId) {
        return memoService.update(AuthContext.requireUserId(), id, request, sessionId);
    }

    /** 申请或续租编辑权；失败返回 409，调用方继续以只读方式查看。 */
    @PostMapping("/{id}/edit")
    public MemoDtos.MemoDetail acquireEdit(
            @PathVariable long id,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId,
            @RequestHeader(value = "X-Edit-Client", required = false) String clientLabel) {
        return memoService.acquireEdit(AuthContext.requireUserId(), id, sessionId, clientLabel);
    }

    @DeleteMapping("/{id}/edit")
    public void releaseEdit(
            @PathVariable long id,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId) {
        memoService.releaseEdit(AuthContext.requireUserId(), id, sessionId);
    }

    /** 冲突时把当前草稿另存为一条新笔记，并取得新笔记的编辑权。 */
    @PostMapping("/{id}/copy")
    public MemoDtos.MemoDetail copyConflict(
            @PathVariable long id,
            @Valid @RequestBody MemoDtos.MemoRequest request,
            @RequestHeader(value = "X-Edit-Session", required = false) String sessionId,
            @RequestHeader(value = "X-Edit-Client", required = false) String clientLabel) {
        return memoService.copyConflict(AuthContext.requireUserId(), id, request,
                sessionId, clientLabel);
    }

    @PostMapping("/{id}/pin")
    public void pin(@PathVariable long id, @RequestBody MemoDtos.PinRequest request) {
        memoService.pin(AuthContext.requireUserId(), id, request.pinned());
    }

    @PostMapping("/{id}/share")
    public MemoDtos.ShareResponse share(@PathVariable long id,
                                        @RequestBody MemoDtos.ShareRequest request) {
        return memoService.share(AuthContext.requireUserId(), id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        memoService.delete(AuthContext.requireUserId(), id);
    }
}
