package org.shiyi.memo.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.file.entity.FileAsset;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图片缩略图：首次访问时生成并缓存到磁盘，避免列表页加载几 MB 的原图。
 */
@Slf4j
@Service
public class ThumbnailService {

    private static final long MAX_SOURCE_BYTES = 40L * 1024 * 1024;
    private static final int MIN_WIDTH = 80;
    private static final int MAX_WIDTH = 1200;

    private final Path root;
    private final FileStorageService storage;

    public ThumbnailService(AppProperties properties, FileStorageService storage) throws IOException {
        this.root = Path.of(properties.getStorage().getThumbnailRoot()).toAbsolutePath().normalize();
        this.storage = storage;
        Files.createDirectories(root);
    }

    /**
     * @return 缩略图路径；文件不是图片或格式不支持（例如 iPhone 的 HEIC）时返回 null
     */
    public Path getOrCreate(FileAsset asset, Integer requestedWidth) {
        if (!"image".equals(asset.getKind()) || asset.getSize() > MAX_SOURCE_BYTES) {
            return null;
        }
        int width = clamp(requestedWidth);
        Path dir = root.resolve(String.valueOf(asset.getId()));
        Path target = dir.resolve(width + ".jpg");
        if (Files.exists(target)) {
            return target;
        }
        Path source = storage.resolve(asset.getStoragePath());
        if (!Files.exists(source)) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) {
                return null;
            }
            BufferedImage scaled = scale(original, width);
            Files.createDirectories(dir);
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            if (!ImageIO.write(scaled, "jpg", temp.toFile())) {
                Files.deleteIfExists(temp);
                return null;
            }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | OutOfMemoryError ex) {
            log.warn("生成缩略图失败 fileId={}", asset.getId(), ex);
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage original, int width) {
        int sourceWidth = original.getWidth();
        int sourceHeight = original.getHeight();
        if (sourceWidth <= width) {
            return original;
        }
        int height = Math.max(1, (int) Math.round(sourceHeight * (width / (double) sourceWidth)));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static int clamp(Integer requestedWidth) {
        if (requestedWidth == null) {
            return 480;
        }
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, requestedWidth));
    }

    /** 附件删除时顺带清掉它的缩略图目录 */
    public void deleteFor(long fileId) {
        Path dir = root.resolve(String.valueOf(fileId));
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("删除缩略图失败：{}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("清理缩略图目录失败：{}", dir, ex);
        }
    }
}
