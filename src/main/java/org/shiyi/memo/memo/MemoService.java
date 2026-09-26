package org.shiyi.memo.memo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.memo.dto.MemoDtos;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.shiyi.memo.tag.TagService;
import org.shiyi.memo.tag.dto.TagDtos;
import org.shiyi.memo.tag.entity.Tag;
import org.shiyi.memo.tag.mapper.TagMapper;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final MemoMapper memoMapper;
    private final TagMapper tagMapper;
    private final FileAssetMapper fileMapper;
    private final TagService tagService;
    private final FileService fileService;
    private final UserService userService;

    public PageResult<MemoDtos.MemoSummary> list(long userId, String keyword, Collection<Long> tagIds,
                                                 Boolean pinned, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(Memo.class).eq(Memo::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(Memo::getTitle, kw).or().like(Memo::getContentMd, kw));
        }
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
                        excerpt(memo.getContentMd()),
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

    public MemoDtos.MemoDetail detail(long userId, long memoId) {
        Memo memo = requireOwned(userId, memoId);
        List<TagDtos.TagDto> tags = tagsOf(List.of(memoId), userId).getOrDefault(memoId, List.of());
        List<FileDtos.FileDto> files = fileService.listByMemo(userId, memoId);
        return new MemoDtos.MemoDetail(memo.getId(), memo.getTitle(), memo.getContentMd(),
                Boolean.TRUE.equals(memo.getPinned()), tags, files,
                Boolean.TRUE.equals(memo.getShareEnabled()), memo.getShareToken(),
                memo.getCreatedAt(), memo.getUpdatedAt());
    }

    @Transactional
    public MemoDtos.MemoDetail create(long userId, MemoDtos.MemoRequest request) {
        Memo memo = new Memo();
        memo.setUserId(userId);
        memo.setContentMd(request.contentMd() == null ? "" : request.contentMd());
        memo.setTitle(resolveTitle(request.title(), memo.getContentMd()));
        boolean pinned = Boolean.TRUE.equals(request.pinned());
        memo.setPinned(pinned);
        memo.setPinnedAt(pinned ? LocalDateTime.now() : null);
        memoMapper.insert(memo);
        syncTags(userId, memo.getId(), request.tagNames());
        return detail(userId, memo.getId());
    }

    @Transactional
    public MemoDtos.MemoDetail update(long userId, long memoId, MemoDtos.MemoRequest request) {
        Memo memo = requireOwned(userId, memoId);
        String content = request.contentMd();
        Memo patch = new Memo();
        patch.setId(memo.getId());
        if (content != null) {
            patch.setContentMd(content);
        }
        patch.setTitle(resolveTitle(request.title(), content != null ? content : memo.getContentMd()));
        if (request.pinned() != null) {
            boolean pinned = request.pinned();
            patch.setPinned(pinned);
            patch.setPinnedAt(pinned ? (memo.getPinnedAt() != null ? memo.getPinnedAt() : LocalDateTime.now()) : null);
        }
        memoMapper.updateById(patch);
        if (request.tagNames() != null) {
            syncTags(userId, memo.getId(), request.tagNames());
        }
        return detail(userId, memoId);
    }

    @Transactional
    public void pin(long userId, long memoId, boolean pinned) {
        Memo memo = requireOwned(userId, memoId);
        Memo patch = new Memo();
        patch.setId(memo.getId());
        patch.setPinned(pinned);
        patch.setPinnedAt(pinned ? LocalDateTime.now() : null);
        memoMapper.updateById(patch);
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
        return new MemoDtos.PublicMemoDetail(memo.getId(), memo.getTitle(), memo.getContentMd(),
                owner.getId(), owner.getNickname(), memo.getUpdatedAt());
    }

    /** 删除就是真删：附件、磁盘文件、标签关联一起清掉 */
    @Transactional
    public void delete(long userId, long memoId) {
        Memo memo = requireOwned(userId, memoId);
        fileService.deleteByMemo(userId, memoId);
        tagMapper.unlinkAll(memo.getId());
        memoMapper.deleteById(memo.getId());
    }

    public Memo requireOwned(long userId, long memoId) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null || !memo.getUserId().equals(userId)) {
            throw ApiException.notFound("记录不存在或已删除");
        }
        return memo;
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

    /**
     * 标题留空时，取正文第一行非空内容当标题。
     */
    static String resolveTitle(String title, String contentMd) {
        if (StringUtils.hasText(title)) {
            return title.trim().length() > 255 ? title.trim().substring(0, 255) : title.trim();
        }
        if (!StringUtils.hasText(contentMd)) {
            return "无标题";
        }
        for (String line : contentMd.split("\\R")) {
            String clean = line.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (!clean.isEmpty()) {
                return clean.length() > 60 ? clean.substring(0, 60) : clean;
            }
        }
        return "无标题";
    }

    /**
     * 列表摘要：粗略去掉 Markdown 记号，只保留纯文本前 140 个字。
     */
    public static String excerpt(String contentMd) {
        if (!StringUtils.hasText(contentMd)) {
            return "";
        }
        String text = contentMd
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                // 列表 / 待办的记号去掉，摘要里只留文字
                .replaceAll("(?m)^\\s{0,3}(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]]\\s*)?", "")
                .replaceAll("[*_`>~]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 140 ? text.substring(0, 140) + "…" : text;
    }
}
