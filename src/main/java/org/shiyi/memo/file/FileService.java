package org.shiyi.memo.file;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.file.storage.FileStorageService;
import org.shiyi.memo.file.storage.ThumbnailService;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.entity.UserLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final long IMAGE_SIZE_PROBE_LIMIT = 40L * 1024 * 1024;

    private final FileAssetMapper fileMapper;
    private final MemoMapper memoMapper;
    private final UserService userService;
    private final FileStorageService storage;
    private final ThumbnailService thumbnails;

    @Transactional
    public FileDtos.FileDto upload(long userId, MultipartFile multipart, Long memoId) {
        if (multipart == null || multipart.isEmpty()) {
            throw ApiException.badRequest("请选择要上传的文件");
        }
        AppUser user = userService.requireEnabled(userId);
        UserLevel level = userService.levelOf(user);
        long size = multipart.getSize();

        // 不再限制单个文件大小，只受总空间约束
        long used = user.getStorageUsed() == null ? 0 : user.getStorageUsed();
        if (used + size > level.getMaxStorage()) {
            throw new ApiException(413, "剩余空间不足：当前等级「" + level.getName() + "」共 "
                    + humanSize(level.getMaxStorage()) + "，已使用 " + humanSize(used)
                    + "，还需 " + humanSize(size) + "，可在个人中心查看用量");
        }
        if (memoId != null) {
            requireMemo(userId, memoId);
        }

        String originalName = sanitizeName(multipart.getOriginalFilename());
        String ext = extensionOf(originalName);
        String mime = detectMime(multipart, ext);
        String kind = kindOf(mime, ext);
        String relativePath = buildRelativePath(userId, ext);

        String stored;
        try (InputStream in = multipart.getInputStream()) {
            stored = storage.store(in, relativePath);
        } catch (IOException ex) {
            log.error("写入文件失败 userId={} name={}", userId, originalName, ex);
            throw new ApiException(500, "文件写入失败，请重试");
        }

        FileAsset asset = new FileAsset();
        asset.setUserId(userId);
        asset.setMemoId(memoId);
        asset.setOriginalName(originalName);
        asset.setExt(ext);
        asset.setMime(mime);
        asset.setSize(size);
        asset.setKind(kind);
        asset.setStoragePath(stored);
        try {
            fileMapper.insert(asset);
        } catch (RuntimeException ex) {
            storage.delete(stored);
            throw ex;
        }
        userService.addStorageUsed(userId, size);

        if ("image".equals(kind)) {
            probeImageSize(asset);
        }
        log.info("文件上传成功 user={} fileId={} name={} size={}", userId, asset.getId(), originalName, size);
        return FileDtos.FileDto.from(asset);
    }

    public PageResult<FileDtos.FileDto> list(long userId, Long memoId, String kind, long page, long size) {
        IPage<FileAsset> result = fileMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(FileAsset.class)
                        .eq(FileAsset::getUserId, userId)
                        .eq(memoId != null, FileAsset::getMemoId, memoId)
                        .eq(kind != null && !kind.isBlank(), FileAsset::getKind, kind)
                        .orderByDesc(FileAsset::getId));
        return PageResult.of(result.getRecords().stream().map(FileDtos.FileDto::from).toList(),
                result.getTotal(), page, size);
    }

    public List<FileDtos.FileDto> listByMemo(long userId, long memoId) {
        return fileMapper.selectList(Wrappers.lambdaQuery(FileAsset.class)
                        .eq(FileAsset::getUserId, userId)
                        .eq(FileAsset::getMemoId, memoId)
                        .orderByAsc(FileAsset::getId))
                .stream().map(FileDtos.FileDto::from).toList();
    }

    public FileAsset requireOwned(long userId, long fileId) {
        FileAsset asset = fileMapper.selectById(fileId);
        if (asset == null || !asset.getUserId().equals(userId)) {
            throw ApiException.notFound("文件不存在");
        }
        return asset;
    }

    /** 用户读取附件：被屏蔽的直接拒绝 */
    public FileAsset requireReadable(long userId, long fileId) {
        FileAsset asset = requireOwned(userId, fileId);
        if (Boolean.TRUE.equals(asset.getBlocked())) {
            throw ApiException.forbidden("该文件已被管理员屏蔽"
                    + (asset.getBlockedReason() == null || asset.getBlockedReason().isBlank()
                    ? "" : "：" + asset.getBlockedReason()));
        }
        return asset;
    }

    /** 公开分享中的附件：必须确实属于当前仍启用的那条分享记录。 */
    public FileAsset requirePublicReadable(long memoId, long fileId) {
        FileAsset asset = fileMapper.selectById(fileId);
        if (asset == null || !Long.valueOf(memoId).equals(asset.getMemoId())) {
            throw ApiException.forbidden("该附件不属于当前分享");
        }
        return asset;
    }

    /** 后台用：不看归属，也不管是否被屏蔽 */
    public FileAsset getAny(long fileId) {
        FileAsset asset = fileMapper.selectById(fileId);
        if (asset == null) {
            throw ApiException.notFound("文件不存在");
        }
        return asset;
    }

    @Transactional
    public FileAsset block(long fileId, String reason) {
        FileAsset asset = getAny(fileId);
        FileAsset patch = new FileAsset();
        patch.setId(asset.getId());
        patch.setBlocked(true);
        patch.setBlockedReason(reason);
        patch.setBlockedAt(LocalDateTime.now());
        fileMapper.updateById(patch);
        return getAny(fileId);
    }

    @Transactional
    public FileAsset unblock(long fileId) {
        FileAsset asset = getAny(fileId);
        FileAsset patch = new FileAsset();
        patch.setId(asset.getId());
        patch.setBlocked(false);
        patch.setBlockedReason(null);
        patch.setBlockedAt(null);
        fileMapper.updateById(patch);
        return getAny(fileId);
    }

    public Path pathOf(FileAsset asset) {
        Path path = storage.resolve(asset.getStoragePath());
        if (!Files.exists(path)) {
            throw ApiException.notFound("文件已丢失，可能被手动删除");
        }
        return path;
    }

    public Path thumbnailOf(FileAsset asset, Integer width) {
        return thumbnails.getOrCreate(asset, width);
    }

    @Transactional
    public void attach(long userId, long fileId, Long memoId) {
        FileAsset asset = requireOwned(userId, fileId);
        if (memoId != null) {
            requireMemo(userId, memoId);
        }
        FileAsset patch = new FileAsset();
        patch.setId(asset.getId());
        patch.setMemoId(memoId);
        fileMapper.updateById(patch);
    }

    @Transactional
    public void delete(long userId, long fileId) {
        FileAsset asset = requireOwned(userId, fileId);
        removeAsset(asset);
    }

    @Transactional
    public void deleteByMemo(long userId, long memoId) {
        List<FileAsset> assets = fileMapper.selectList(Wrappers.lambdaQuery(FileAsset.class)
                .eq(FileAsset::getUserId, userId)
                .eq(FileAsset::getMemoId, memoId));
        assets.forEach(this::removeAsset);
    }

    private void removeAsset(FileAsset asset) {
        fileMapper.deleteById(asset.getId());
        storage.delete(asset.getStoragePath());
        thumbnails.deleteFor(asset.getId());
        userService.addStorageUsed(asset.getUserId(), -asset.getSize());
    }

    private void requireMemo(long userId, long memoId) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null || !memo.getUserId().equals(userId)) {
            throw ApiException.notFound("记录不存在");
        }
    }

    private void probeImageSize(FileAsset asset) {
        if (asset.getSize() > IMAGE_SIZE_PROBE_LIMIT) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(storage.resolve(asset.getStoragePath()).toFile());
            if (image == null) {
                return;
            }
            FileAsset patch = new FileAsset();
            patch.setId(asset.getId());
            patch.setWidth(image.getWidth());
            patch.setHeight(image.getHeight());
            fileMapper.updateById(patch);
            asset.setWidth(image.getWidth());
            asset.setHeight(image.getHeight());
        } catch (IOException | OutOfMemoryError ex) {
            log.debug("读取图片尺寸失败 fileId={}", asset.getId(), ex);
        }
    }

    private static String buildRelativePath(long userId, String ext) {
        String name = UUID.randomUUID().toString().replace("-", "");
        String suffix = ext.isEmpty() ? "" : "." + ext;
        return userId + "/" + LocalDate.now().format(YEAR_MONTH) + "/" + name + suffix;
    }

    private static String sanitizeName(String original) {
        String name = original == null || original.isBlank() ? "未命名文件" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "未命名文件";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return ext.length() > 16 ? ext.substring(0, 16) : ext;
    }

    private static String detectMime(MultipartFile file, String ext) {
        String mime = file.getContentType();
        if (mime != null && !mime.isBlank() && !"application/octet-stream".equals(mime)) {
            return mime;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "bmp" -> "image/bmp";
            case "svg" -> "image/svg+xml";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "pdf" -> "application/pdf";
            case "txt", "md" -> "text/plain";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private static String kindOf(String mime, String ext) {
        if (mime.startsWith("image/") || List.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic").contains(ext)) {
            return "image";
        }
        if (mime.startsWith("video/") || List.of("mp4", "mov", "avi", "mkv", "webm").contains(ext)) {
            return "video";
        }
        if (mime.startsWith("audio/") || List.of("mp3", "wav", "m4a", "flac", "aac").contains(ext)) {
            return "audio";
        }
        return "other";
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes / 1024.0;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return (value >= 100 ? String.format("%.0f", value) : String.format("%.1f", value)) + " " + units[unit];
    }
}
