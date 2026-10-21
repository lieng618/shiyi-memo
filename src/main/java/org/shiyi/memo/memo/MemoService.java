package org.shiyi.memo.memo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.friend.FriendService;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.memo.dto.MemoDtos;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.entity.MemoEditLease;
import org.shiyi.memo.memo.mapper.MemoEditLeaseMapper;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.shiyi.memo.realtime.RealtimeEventService;
import org.shiyi.memo.security.AuthContext;
import org.shiyi.memo.security.AuthUser;
import org.shiyi.memo.tag.TagService;
import org.shiyi.memo.tag.dto.TagDtos;
import org.shiyi.memo.tag.entity.Tag;
import org.shiyi.memo.tag.mapper.TagMapper;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemoService {

    /** 90 秒没有续租就允许其他设备接管，前端每 20 秒续租一次。 */
    private static final long EDIT_LEASE_SECONDS = 90;

    private final MemoMapper memoMapper;
    private final MemoEditLeaseMapper editLeaseMapper;
    private final TagMapper tagMapper;
    private final FileAssetMapper fileMapper;
    private final TagService tagService;
    private final FileService fileService;
    private final UserService userService;
    private final FriendService friendService;
    private final RealtimeEventService realtimeEventService;

    public PageResult<MemoDtos.MemoSummary> list(long userId, String keyword, Collection<Long> tagIds,
                                                 Boolean pinned, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(Memo.class).eq(Memo::getUserId, userId);
        MemoSearch.applyKeyword(wrapper, keyword);
        if (pinned != null) {
            wrapper.eq(Memo::getPinned, pinned);
        }
        List<Long> ownedTagIds = ownedTagIds(userId, tagIds);
        if (!ownedTagIds.isEmpty()) {
            wrapper.inSql(Memo::getId, "SELECT memo_id FROM memo_tag WHERE tag_id IN ("
                    + ownedTagIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
        }
        wrapper.orderByDesc(Memo::getPinned, Memo::getPinnedAt, Memo::getUpdatedAt);

        IPage<Memo> result = memoMapper.selectPage(new Page<>(page, size), wrapper);
        List<Memo> memos = result.getRecords();
        if (memos.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), page, size);
        }

        List<Long> memoIds = memos.stream().map(Memo::getId).toList();
        Map<Long, List<TagDtos.TagDto>> tagMap = tagsOf(memoIds, userId);
        Map<Long, Long> fileCounts = fileCountsOf(memoIds);
        Map<Long, String> covers = coverUrlsOf(memoIds);

        List<MemoDtos.MemoSummary> records = memos.stream()
                .map(memo -> new MemoDtos.MemoSummary(memo.getId(), memo.getTitle(),
                        summaryExcerpt(memo),
                        Boolean.TRUE.equals(memo.getPinned()),
                        tagMap.getOrDefault(memo.getId(), List.of()),
                        fileCounts.getOrDefault(memo.getId(), 0L),
                        covers.get(memo.getId()),
                        Boolean.TRUE.equals(memo.getShareEnabled()),
                        memo.getShareToken(),
                        memo.getCreatedAt(), memo.getUpdatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public MemoDtos.MemoDetail detail(long userId, long memoId, String sessionId) {
        Memo memo = requireOwned(userId, memoId);
        List<TagDtos.TagDto> tags = tagsOf(List.of(memoId), userId).getOrDefault(memoId, List.of());
        List<FileDtos.FileDto> files = fileService.listByMemo(userId, memoId);
        return new MemoDtos.MemoDetail(memo.getId(), memo.getTitle(), memo.getContentHtml(),
                Boolean.TRUE.equals(memo.getPinned()), tags, files,
                Boolean.TRUE.equals(memo.getShareEnabled()), memo.getShareToken(),
                memo.getVersion() == null ? 0L : memo.getVersion(), leaseInfo(memoId, sessionId),
                memo.getCreatedAt(), memo.getUpdatedAt());
    }

    @Transactional
    public MemoDtos.MemoDetail create(long userId, MemoDtos.MemoRequest request,
                                      String sessionId, String clientLabel) {
        String session = requireSession(sessionId);
        Memo memo = new Memo();
        memo.setUserId(userId);
        memo.setContentHtml(request.contentHtml() == null ? "" : request.contentHtml());
        memo.setTitle(resolveTitle(request.title(), memo.getContentHtml()));
        deriveSearchText(memo);
        memo.setVersion(0L);
        boolean pinned = Boolean.TRUE.equals(request.pinned());
        memo.setPinned(pinned);
        memo.setPinnedAt(pinned ? LocalDateTime.now() : null);
        memoMapper.insert(memo);
        syncTags(userId, memo.getId(), request.tagNames());
        insertLease(userId, memo.getId(), session, normalizeClientLabel(clientLabel));
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("create", memo.getId(), memo.getVersion(), session));
        return detail(userId, memo.getId(), session);
    }

    @Transactional
    public MemoDtos.MemoDetail update(long userId, long memoId, MemoDtos.MemoRequest request,
                                      String sessionId) {
        String session = requireSession(sessionId);
        if (request.expectedVersion() == null) {
            throw ApiException.badRequest("缺少笔记版本号");
        }
        Memo memo = requireOwned(userId, memoId);
        String content = request.contentHtml();
        Memo patch = new Memo();
        patch.setId(memo.getId());
        if (content != null) {
            patch.setContentHtml(content);
            deriveSearchText(patch);
        }
        patch.setTitle(resolveTitle(request.title(), content != null ? content : memo.getContentHtml()));
        if (request.pinned() != null) {
            boolean pinned = request.pinned();
            patch.setPinned(pinned);
            patch.setPinnedAt(pinned ? (memo.getPinnedAt() != null ? memo.getPinnedAt() : LocalDateTime.now()) : null);
        }
        int updated = memoMapper.updateWithLease(patch, userId, request.expectedVersion(),
                session, LocalDateTime.now());
        if (updated == 0) {
            MemoEditLease lease = editLeaseMapper.selectById(memoId);
            if (!isLeaseActive(lease, LocalDateTime.now()) || !session.equals(lease.getSessionId())) {
                throw ApiException.conflict("编辑权已失效，请先重新取得编辑权");
            }
            throw ApiException.conflict("笔记已在其他设备更新，请处理冲突后再保存");
        }
        if (content != null) {
            fileService.deleteUnreferenced(userId, memoId, content);
        }
        if (request.tagNames() != null) {
            syncTags(userId, memo.getId(), request.tagNames());
        }
        MemoDtos.MemoDetail result = detail(userId, memoId, session);
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("update", memoId, result.version(), session));
        return result;
    }

    /**
     * 申请或续租编辑权。先锁 memo 行，保证两个设备同时点击编辑时只有一个成功。
     */
    @Transactional
    public MemoDtos.MemoDetail acquireEdit(long userId, long memoId,
                                           String sessionId, String clientLabel) {
        String session = requireSession(sessionId);
        if (memoMapper.lockOwned(userId, memoId) == null) {
            throw ApiException.notFound("记录不存在或已删除");
        }

        LocalDateTime now = LocalDateTime.now();
        MemoEditLease lease = editLeaseMapper.selectById(memoId);
        boolean changedHolder = !isLeaseActive(lease, now)
                || !session.equals(lease.getSessionId());
        if (!changedHolder) {
            editLeaseMapper.updateById(leasePatch(memoId, session,
                    normalizeClientLabel(clientLabel), now));
            return detail(userId, memoId, session);
        }

        if (isLeaseActive(lease, now)) {
            throw ApiException.conflict(normalizeClientLabel(lease.getClientLabel()) + " 正在编辑这篇笔记");
        }

        upsertLease(userId, memoId, lease, session,
                normalizeClientLabel(clientLabel), now);
        MemoDtos.MemoDetail result = detail(userId, memoId, session);
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("lease", memoId, result.version(), session));
        return result;
    }

    @Transactional
    public void releaseEdit(long userId, long memoId, String sessionId) {
        String session = requireSession(sessionId);
        if (memoMapper.lockOwned(userId, memoId) == null) {
            return;
        }
        MemoEditLease lease = editLeaseMapper.selectById(memoId);
        if (lease == null || !session.equals(lease.getSessionId())) {
            return;
        }
        editLeaseMapper.deleteById(memoId);
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("lease", memoId, null, session));
    }

    /**
     * 冲突草稿另存为新笔记。正文引用的附件也会复制成新笔记自己的附件，
     * 避免原笔记随后清理附件时把副本弄坏。
     */
    @Transactional
    public MemoDtos.MemoDetail copyConflict(long userId, long sourceMemoId,
                                            MemoDtos.MemoRequest request,
                                            String sessionId, String clientLabel) {
        String session = requireSession(sessionId);
        Memo source = requireOwned(userId, sourceMemoId);
        String content = request.contentHtml() == null ? source.getContentHtml() : request.contentHtml();

        Memo copy = new Memo();
        copy.setUserId(userId);
        copy.setContentHtml(content == null ? "" : content);
        copy.setTitle(conflictCopyTitle(resolveTitle(request.title(), copy.getContentHtml())));
        deriveSearchText(copy);
        copy.setVersion(0L);
        copy.setPinned(false);
        copy.setPinnedAt(null);
        memoMapper.insert(copy);

        String clonedContent = fileService.cloneReferencedFiles(userId, copy.getId(), copy.getContentHtml());
        if (!clonedContent.equals(copy.getContentHtml())) {
            Memo patch = new Memo();
            patch.setId(copy.getId());
            patch.setContentHtml(clonedContent);
            deriveSearchText(patch);
            memoMapper.updateById(patch);
        }
        syncTags(userId, copy.getId(), request.tagNames());
        insertLease(userId, copy.getId(), session, normalizeClientLabel(clientLabel));
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("create", copy.getId(), 0L, session));
        return detail(userId, copy.getId(), session);
    }

    @Transactional
    public void pin(long userId, long memoId, boolean pinned) {
        Memo memo = requireOwned(userId, memoId);
        Memo patch = new Memo();
        patch.setId(memo.getId());
        patch.setPinned(pinned);
        patch.setPinnedAt(pinned ? LocalDateTime.now() : null);
        memoMapper.updateById(patch);
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("update", memoId, memo.getVersion(), null));
    }

    @Transactional
    public MemoDtos.ShareResponse share(long userId, long memoId, boolean enabled) {
        Memo memo = requireOwned(userId, memoId);
        Memo patch = new Memo();
        patch.setId(memo.getId());
        patch.setShareEnabled(enabled);

        String token = memo.getShareToken();
        if (enabled) {
            if (!StringUtils.hasText(token)) {
                token = UUID.randomUUID().toString().replace("-", "");
                patch.setShareToken(token);
            }
            patch.setSharedAt(LocalDateTime.now());
        }
        memoMapper.updateById(patch);
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("update", memoId, memo.getVersion(), null));
        return new MemoDtos.ShareResponse(enabled, token);
    }

    /**
     * 公开读取只认有效分享令牌。令牌存在但已关闭时返回 403，前端可以明确展示“没有权限”。
     */
    public Memo requirePublicShare(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw ApiException.notFound("分享链接无效");
        }
        Memo memo = memoMapper.selectOne(Wrappers.lambdaQuery(Memo.class)
                .eq(Memo::getShareToken, rawToken.trim())
                .last("limit 1"));
        if (memo == null) {
            throw ApiException.notFound("分享链接无效");
        }
        if (!Boolean.TRUE.equals(memo.getShareEnabled())) {
            throw ApiException.forbidden("分享已关闭，暂无访问权限");
        }
        return memo;
    }

    public MemoDtos.PublicMemoDetail publicDetail(String token) {
        Memo memo = requirePublicShare(token);
        AppUser owner = userService.get(memo.getUserId());
        FileAsset avatar = fileService.publicAvatar(owner);
        AuthUser viewer = AuthContext.get();
        boolean friend = viewer != null && friendService.isFriend(viewer.id(), owner.getId());
        return new MemoDtos.PublicMemoDetail(memo.getId(), memo.getTitle(), memo.getContentHtml(),
                owner.getId(), owner.getNickname(), ownerAvatarUrl(token, avatar), friend,
                memo.getUpdatedAt());
    }

    public FileAsset publicOwnerAvatar(String token) {
        Memo memo = requirePublicShare(token);
        AppUser owner = userService.get(memo.getUserId());
        FileAsset avatar = fileService.publicAvatar(owner);
        if (avatar == null) {
            throw ApiException.notFound("头像不存在");
        }
        return avatar;
    }

    /** 删除就是真删：附件、磁盘文件、标签关联一起清掉 */
    @Transactional
    public void delete(long userId, long memoId) {
        Memo memo = requireOwned(userId, memoId);
        fileService.deleteByMemo(userId, memoId);
        tagMapper.unlinkAll(memo.getId());
        editLeaseMapper.deleteById(memo.getId());
        memoMapper.deleteById(memo.getId());
        realtimeEventService.sendAfterCommit(userId, "memo",
                memoEvent("delete", memoId, null, null));
    }

    public Memo requireOwned(long userId, long memoId) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null || !memo.getUserId().equals(userId)) {
            throw ApiException.notFound("记录不存在或已删除");
        }
        return memo;
    }

    private void insertLease(long userId, long memoId, String sessionId, String clientLabel) {
        LocalDateTime now = LocalDateTime.now();
        MemoEditLease lease = new MemoEditLease();
        lease.setMemoId(memoId);
        lease.setUserId(userId);
        lease.setSessionId(sessionId);
        lease.setClientLabel(clientLabel);
        lease.setAcquiredAt(now);
        lease.setExpiresAt(now.plusSeconds(EDIT_LEASE_SECONDS));
        editLeaseMapper.insert(lease);
    }

    private void upsertLease(long userId, long memoId, MemoEditLease existing,
                             String sessionId, String clientLabel, LocalDateTime now) {
        if (existing == null) {
            insertLease(userId, memoId, sessionId, clientLabel);
            return;
        }
        MemoEditLease patch = leasePatch(memoId, sessionId, clientLabel, now);
        patch.setUserId(userId);
        patch.setAcquiredAt(now);
        editLeaseMapper.updateById(patch);
    }

    private static MemoEditLease leasePatch(long memoId, String sessionId,
                                            String clientLabel, LocalDateTime now) {
        MemoEditLease patch = new MemoEditLease();
        patch.setMemoId(memoId);
        patch.setSessionId(sessionId);
        patch.setClientLabel(clientLabel);
        patch.setExpiresAt(now.plusSeconds(EDIT_LEASE_SECONDS));
        return patch;
    }

    private MemoDtos.EditLeaseInfo leaseInfo(long memoId, String sessionId) {
        MemoEditLease lease = editLeaseMapper.selectById(memoId);
        if (!isLeaseActive(lease, LocalDateTime.now())) {
            return new MemoDtos.EditLeaseInfo(false, null, null);
        }
        String holderLabel = normalizeClientLabel(lease.getClientLabel());
        return new MemoDtos.EditLeaseInfo(
                StringUtils.hasText(sessionId) && sessionId.equals(lease.getSessionId()),
                holderLabel, lease.getExpiresAt());
    }

    private static boolean isLeaseActive(MemoEditLease lease, LocalDateTime now) {
        return lease != null && lease.getExpiresAt() != null && lease.getExpiresAt().isAfter(now);
    }

    private static String requireSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)
                || sessionId.length() < 16
                || sessionId.length() > 64
                || !sessionId.matches("[A-Za-z0-9_-]+")) {
            throw ApiException.badRequest("缺少有效的编辑会话标识");
        }
        return sessionId;
    }

    private static String normalizeClientLabel(String label) {
        if (!StringUtils.hasText(label)) {
            return "其他设备";
        }
        String decoded;
        try {
            decoded = UriUtils.decode(label, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "其他设备";
        }
        String clean = decoded.replaceAll("[\\p{Cntrl}]", "").trim();
        if (clean.isEmpty()) {
            return "其他设备";
        }
        return clean.length() > 128 ? clean.substring(0, 128) : clean;
    }

    private static String conflictCopyTitle(String title) {
        String suffix = "（冲突副本）";
        String clean = StringUtils.hasText(title) ? title.trim() : "无标题";
        int maxBase = 255 - suffix.length();
        if (clean.length() > maxBase) {
            clean = clean.substring(0, maxBase);
        }
        return clean + suffix;
    }

    private static Map<String, Object> memoEvent(String event, long memoId,
                                                 Long version, String sessionId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("memoId", memoId);
        payload.put("version", version);
        payload.put("sessionId", sessionId);
        return payload;
    }

    private void syncTags(long userId, long memoId, Collection<String> tagNames) {
        tagMapper.unlinkAll(memoId);
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }
        Set<Long> tagIds = new HashSet<>();
        for (Tag tag : tagService.resolveByNames(userId, tagNames)) {
            tagIds.add(tag.getId());
        }
        tagIds.forEach(tagId -> tagMapper.link(memoId, tagId));
    }

    private List<Long> ownedTagIds(long userId, Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectList(Wrappers.lambdaQuery(Tag.class)
                        .eq(Tag::getUserId, userId)
                        .in(Tag::getId, tagIds))
                .stream().map(Tag::getId).toList();
    }

    private Map<Long, List<TagDtos.TagDto>> tagsOf(List<Long> memoIds, long userId) {
        List<Map<String, Object>> links = tagMapper.selectLinks(memoIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = links.stream()
                .map(row -> ((Number) row.get("tagId")).longValue())
                .collect(Collectors.toSet());
        Map<Long, Tag> tags = tagService.findByIds(userId, tagIds);
        Map<Long, List<TagDtos.TagDto>> result = new HashMap<>();
        for (Map<String, Object> link : links) {
            long memoId = ((Number) link.get("memoId")).longValue();
            long tagId = ((Number) link.get("tagId")).longValue();
            Tag tag = tags.get(tagId);
            if (tag == null) {
                continue;
            }
            result.computeIfAbsent(memoId, key -> new ArrayList<>())
                    .add(new TagDtos.TagDto(tag.getId(), tag.getName(), tag.getColor(), null));
        }
        return result;
    }

    private Map<Long, Long> fileCountsOf(List<Long> memoIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : fileMapper.countByMemoIds(memoIds)) {
            counts.put(((Number) row.get("memoId")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    /** 列表里给带图的记录显示一张小封面 */
    private Map<Long, String> coverUrlsOf(List<Long> memoIds) {
        Map<Long, String> covers = new HashMap<>();
        for (Map<String, Object> row : fileMapper.selectCoverImages(memoIds)) {
            long memoId = ((Number) row.get("memoId")).longValue();
            long fileId = ((Number) row.get("fileId")).longValue();
            covers.put(memoId, "/api/files/" + fileId + "/thumb?w=320");
        }
        return covers;
    }

    private static String ownerAvatarUrl(String token, FileAsset avatar) {
        if (avatar == null) {
            return null;
        }
        return "/api/public/memos/" + UriUtils.encodePathSegment(token, StandardCharsets.UTF_8)
                + "/owner-avatar";
    }

    /**
     * 标题留空时，取正文第一段文字当标题。
     */
    public static String resolveTitle(String title, String contentHtml) {
        if (StringUtils.hasText(title)) {
            return title.trim().length() > 255 ? title.trim().substring(0, 255) : title.trim();
        }
        if (!StringUtils.hasText(contentHtml)) {
            return "无标题";
        }
        // 去掉标签和实体，只留纯文本；行内样式的 span 也会被剥掉
        String text = stripHtml(contentHtml);
        for (String line : text.split("\\R")) {
            String clean = line.trim();
            if (!clean.isEmpty()) {
                return clean.length() > 60 ? clean.substring(0, 60) : clean;
            }
        }
        return "无标题";
    }

    /**
     * 列表摘要：剥掉 HTML 标签只保留纯文本前 140 个字。
     */
    public static String excerpt(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return "";
        }
        return excerptFrom(stripHtml(contentHtml));
    }

    /**
     * 保存时顺手算好搜索用纯文本和列表摘要：列表页每次都要给整页笔记剥一遍 HTML，
     * 放在写入侧只算一次，搜索也不必再去扫富文本。
     */
    private static void deriveSearchText(Memo memo) {
        fillDerivedText(memo, memo);
    }

    /** 把去标签纯文本和摘要写进 patch（供写入路径与历史数据回填共用） */
    public static void fillDerivedText(Memo source, Memo patch) {
        String text = stripHtml(source.getContentHtml() == null ? "" : source.getContentHtml());
        patch.setContentText(text);
        patch.setExcerpt(excerptFrom(text));
    }

    /** 老数据还没回填摘要列时现场算一份，保证列表不空白 */
    public static String summaryExcerpt(Memo memo) {
        String stored = memo.getExcerpt();
        return stored == null ? excerpt(memo.getContentHtml()) : stored;
    }

    private static String excerptFrom(String plainText) {
        String text = plainText.replaceAll("\\s+", " ").trim();
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }

    /** 去掉 HTML 标签并还原常见实体，剩下的就是纯文本 */
    private static String stripHtml(String html) {
        return html
                // 块级标签换成换行，避免相邻段落的文字粘在一起
                .replaceAll("(?i)</(?:p|div|h[1-6]|li|blockquote|pre|tr)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?s)<[^>]*>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
}
