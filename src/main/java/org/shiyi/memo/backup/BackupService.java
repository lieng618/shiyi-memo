package org.shiyi.memo.backup;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.backup.dto.BackupDtos;
import org.shiyi.memo.backup.dto.BackupDtos.BackupManifest;
import org.shiyi.memo.backup.dto.BackupDtos.BackupManifest.ManifestFile;
import org.shiyi.memo.backup.dto.BackupDtos.BackupManifest.ManifestMemo;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.memo.MemoService;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.shiyi.memo.tag.TagService;
import org.shiyi.memo.tag.entity.Tag;
import org.shiyi.memo.tag.mapper.TagMapper;
import org.shiyi.memo.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 备份 = manifest.json（笔记 + 标签 + 附件元数据）+ attachments/ 目录（附件本体）。
 * 导入一律把备份内容新增到当前账号，不覆盖不删除，失败整单回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    static final int BACKUP_VERSION = 1;
    private static final String APP_MARKER = "shiyi-memo";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String ATTACHMENT_DIR = "attachments/";
    private static final int MAX_MEMOS = 5000;
    private static final int MAX_ATTACHMENTS = 20000;
    /** 正文里的站内附件链接：/api/files/{id}/... 和公开分享页的两种写法 */
    private static final Pattern FILE_REFERENCE =
            Pattern.compile("(/api/files/|/api/public/memos/[^/\\s]+/files/)(\\d+)");
    private final MemoMapper memoMapper;
    private final TagMapper tagMapper;
    private final FileAssetMapper fileMapper;
    private final TagService tagService;
    private final FileService fileService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /* ---------------------------------------------------------------- 导出 */

    public void writeBackup(long userId, OutputStream out) throws IOException {
        List<Memo> memos = memoMapper.selectList(Wrappers.lambdaQuery(Memo.class)
                .eq(Memo::getUserId, userId)
                .orderByAsc(Memo::getId));
        List<Long> memoIds = memos.stream().map(Memo::getId).toList();
        List<FileAsset> assets = memoIds.isEmpty() ? List.of()
                : fileMapper.selectList(Wrappers.lambdaQuery(FileAsset.class)
                        .eq(FileAsset::getUserId, userId)
                        .in(FileAsset::getMemoId, memoIds)
                        .orderByAsc(FileAsset::getId));

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(objectMapper.writeValueAsBytes(buildManifest(userId, memos, assets)));
            zip.closeEntry();

            for (FileAsset asset : assets) {
                // 被屏蔽的附件用户本就无权读取，不进备份
                if (Boolean.TRUE.equals(asset.getBlocked())) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(attachmentEntryName(asset)));
                try (InputStream in = Files.newInputStream(fileService.pathOf(asset))) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private BackupManifest buildManifest(long userId, List<Memo> memos, List<FileAsset> assets) {
        Map<Long, List<FileAsset>> filesByMemo = assets.stream()
                .collect(Collectors.groupingBy(FileAsset::getMemoId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<String>> tagNames = tagNamesOf(userId, memos.stream().map(Memo::getId).toList());

        List<ManifestMemo> entries = new ArrayList<>(memos.size());
        for (Memo memo : memos) {
            List<ManifestFile> files = filesByMemo.getOrDefault(memo.getId(), List.of()).stream()
                    .map(asset -> new ManifestFile(
                            asset.getId(),
                            asset.getOriginalName(),
                            asset.getMime(),
                            asset.getSize(),
                            Boolean.TRUE.equals(asset.getBlocked()) ? null : attachmentEntryName(asset),
                            Boolean.TRUE.equals(asset.getBlocked())))
                    .toList();
            entries.add(new ManifestMemo(
                    memo.getId(),
                    memo.getTitle(),
                    memo.getContentMd(),
                    Boolean.TRUE.equals(memo.getPinned()),
                    tagNames.getOrDefault(memo.getId(), List.of()),
                    memo.getCreatedAt(),
                    memo.getUpdatedAt(),
                    files));
        }
        return new BackupManifest(BACKUP_VERSION, APP_MARKER, LocalDateTime.now(), entries);
    }

    private Map<Long, List<String>> tagNamesOf(long userId, List<Long> memoIds) {
        if (memoIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> links = tagMapper.selectLinks(memoIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = links.stream()
                .map(row -> ((Number) row.get("tagId")).longValue())
                .collect(Collectors.toSet());
        Map<Long, Tag> tags = tagService.findByIds(userId, tagIds);
        Map<Long, List<String>> result = new HashMap<>();
        for (Map<String, Object> link : links) {
            Tag tag = tags.get(((Number) link.get("tagId")).longValue());
            if (tag == null) {
                continue;
            }
            result.computeIfAbsent(((Number) link.get("memoId")).longValue(), key -> new ArrayList<>())
                    .add(tag.getName());
        }
        return result;
    }

    private static String attachmentEntryName(FileAsset asset) {
        String name = asset.getOriginalName().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        name = slash >= 0 ? name.substring(slash + 1) : name;
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "file-" + asset.getId();
        }
        return ATTACHMENT_DIR + asset.getId() + "/" + name;
    }

    /* ---------------------------------------------------------------- 导入 */

    @Transactional
    public BackupDtos.ImportResult importBackup(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择要导入的备份文件");
        }
        userService.requireEnabled(userId);

        Path temp = null;
        try {
            // multipart 的临时文件归容器管，拷一份自己掌控，用完即删
            temp = Files.createTempFile("shiyi-import-", ".zip");
            file.transferTo(temp);
            return doImport(userId, temp);
        } catch (IOException ex) {
            log.error("备份导入读文件失败 userId={}", userId, ex);
            throw new ApiException(500, "备份文件读取失败，请重试");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件删不掉只是留了点垃圾，不影响结果
                }
            }
        }
    }

    private BackupDtos.ImportResult doImport(long userId, Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            validateEntries(zip);
            BackupManifest manifest = readManifest(zip);

            List<ManifestMemo> memos = manifest.memos() == null ? List.of() : manifest.memos();
            if (memos.size() > MAX_MEMOS) {
                throw ApiException.badRequest("备份里的笔记超过 " + MAX_MEMOS + " 条，无法导入");
            }
            int totalFiles = memos.stream()
                    .mapToInt(memo -> memo.attachments() == null ? 0 : memo.attachments().size())
                    .sum();
            if (totalFiles > MAX_ATTACHMENTS) {
                throw ApiException.badRequest("备份里的附件超过 " + MAX_ATTACHMENTS + " 个，无法导入");
            }

            // 1) 先建笔记拿到新 id，正文里的附件链接等附件落位后再重写。
            //    标题 + 归一化正文都相同的笔记（账号里已有的、或备份内部重复的）不导入
            Map<Long, Memo> memoMap = new LinkedHashMap<>();
            Set<String> seenKeys = loadExistingKeys(userId, memos);
            int skippedMemos = 0;
            for (ManifestMemo memo : memos) {
                String effectiveTitle = MemoService.resolveTitle(memo.title(), memo.contentMd());
                if (!seenKeys.add(dedupKey(effectiveTitle, memo.contentMd()))) {
                    skippedMemos++;
                    continue;
                }
                Memo entity = new Memo();
                entity.setUserId(userId);
                entity.setTitle(effectiveTitle);
                entity.setContentMd(memo.contentMd() == null ? "" : memo.contentMd());
                boolean pinned = Boolean.TRUE.equals(memo.pinned());
                entity.setPinned(pinned);
                entity.setPinnedAt(pinned ? LocalDateTime.now() : null);
                if (memo.createdAt() != null) {
                    entity.setCreatedAt(memo.createdAt());
                }
                if (memo.updatedAt() != null) {
                    entity.setUpdatedAt(memo.updatedAt());
                }
                memoMapper.insert(entity);
                memoMap.put(memo.id(), entity);
            }

            // 2) 附件按清单逐个还原，记录新旧 id 的映射
            Map<Long, Long> fileIdMap = new HashMap<>();
            int attachmentCount = 0;
            int skipped = 0;
            for (ManifestMemo memo : memos) {
                Memo entity = memoMap.get(memo.id());
                if (entity == null || memo.attachments() == null) {
                    continue;
                }
                for (ManifestFile meta : memo.attachments()) {
                    FileAsset asset = restoreAttachment(userId, entity.getId(), zip, meta);
                    if (asset == null) {
                        skipped++;
                        continue;
                    }
                    fileIdMap.put(meta.id(), asset.getId());
                    attachmentCount++;
                }
            }

            // 3) 重写正文里的附件链接，还原标签
            int memoCount = 0;
            for (ManifestMemo memo : memos) {
                Memo entity = memoMap.get(memo.id());
                if (entity == null) {
                    continue;
                }
                String remapped = remapFileIds(entity.getContentMd(), fileIdMap);
                if (!remapped.equals(entity.getContentMd())) {
                    Memo patch = new Memo();
                    patch.setId(entity.getId());
                    patch.setContentMd(remapped);
                    memoMapper.updateById(patch);
                    entity.setContentMd(remapped);
                }
                tagMapper.unlinkAll(entity.getId());
                List<Long> tagIds = tagService.resolveByNames(userId, memo.tags())
                        .stream().map(Tag::getId).toList();
                for (Long tagId : tagIds) {
                    tagMapper.link(entity.getId(), tagId);
                }
                memoCount++;
            }

            log.info("备份导入完成 user={} memos={} attachments={} skippedMemos={} skipped={}",
                    userId, memoCount, attachmentCount, skippedMemos, skipped);
            return new BackupDtos.ImportResult(BACKUP_VERSION, memoCount, attachmentCount, skippedMemos, skipped);
        } catch (IOException ex) {
            log.error("备份压缩包解析失败 userId={}", userId, ex);
            throw new ApiException(400, "备份文件损坏或不是有效的 zip");
        }
    }

    /** 只读清单里声明的条目，其他一律无视；条目路径不会拼进磁盘路径，没有 zip-slip 风险 */
    private static void validateEntries(ZipFile zip) {
        int count = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            if (++count > MAX_ATTACHMENTS + MAX_MEMOS + 16) {
                throw ApiException.badRequest("备份文件条目异常，拒绝导入");
            }
            entries.nextElement();
        }
    }

    private BackupManifest readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
        if (entry == null) {
            throw ApiException.badRequest("这不是拾忆备份文件（缺少 manifest.json）");
        }
        try (InputStream in = zip.getInputStream(entry)) {
            BackupManifest manifest = objectMapper.readValue(in, BackupManifest.class);
            if (manifest.version() != BACKUP_VERSION) {
                throw ApiException.badRequest("备份版本不支持（v" + manifest.version() + "）");
            }
            if (!APP_MARKER.equals(manifest.app())) {
                throw ApiException.badRequest("这不是拾忆备份文件");
            }
            return manifest;
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("备份清单损坏，无法解析");
        }
    }

    /** 返回 null 表示跳过（被屏蔽、缺文件内容或损坏）；写失败会抛异常中止整个导入 */
    private FileAsset restoreAttachment(long userId, long memoId, ZipFile zip, ManifestFile meta) {
        if (meta == null || meta.id() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(meta.blocked()) || !StringUtils.hasText(meta.path())) {
            return null;
        }
        ZipEntry entry = zip.getEntry(meta.path());
        if (entry == null || entry.isDirectory()) {
            log.warn("备份附件缺失 userId={} path={}", userId, meta.path());
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return fileService.importAsset(userId, memoId, meta.name(), meta.mime(),
                    Math.max(0, meta.size()), in);
        } catch (IOException ex) {
            log.error("备份附件读取失败 userId={} path={}", userId, meta.path(), ex);
            throw new ApiException(500, "附件「" + meta.name() + "」读取失败，导入已中止");
        }
    }

    /**
     * 相同笔记的判定键：标题 + 正文。正文里的附件链接先把 id 归一成占位符——
     * 同一篇笔记导入前后附件 id 必然不同，不归一就永远判不出「相同」。
     */
    private Set<String> loadExistingKeys(long userId, List<ManifestMemo> memos) {
        Set<String> titles = memos.stream()
                .map(memo -> MemoService.resolveTitle(memo.title(), memo.contentMd()))
                .collect(Collectors.toSet());
        if (titles.isEmpty()) {
            return new HashSet<>();
        }
        // 只取同题的已有笔记做比对，避免把整个账号的正文载进内存
        List<Memo> existing = memoMapper.selectList(Wrappers.lambdaQuery(Memo.class)
                .select(Memo::getId, Memo::getTitle, Memo::getContentMd)
                .eq(Memo::getUserId, userId)
                .in(Memo::getTitle, titles));
        return existing.stream()
                .map(memo -> dedupKey(memo.getTitle(), memo.getContentMd()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    static String dedupKey(String title, String contentMd) {
        String normalizedTitle = title == null ? "" : title.trim();
        String content = contentMd == null ? "" : contentMd;
        return normalizedTitle + "\n" + FILE_REFERENCE.matcher(content).replaceAll("$1#");
    }

    static String remapFileIds(String contentMd, Map<Long, Long> idMap) {
        String source = contentMd == null ? "" : contentMd;
        if (source.isBlank() || idMap.isEmpty()) {
            return source;
        }
        Matcher matcher = FILE_REFERENCE.matcher(source);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Long newId = null;
            try {
                newId = idMap.get(Long.parseLong(matcher.group(2)));
            } catch (NumberFormatException ignored) {
                // 超出 long 范围的文本不是站内附件 id
            }
            String id = newId == null ? matcher.group(2) : String.valueOf(newId);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(1) + id));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
