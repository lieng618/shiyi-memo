package org.shiyi.memo.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(AppProperties properties) throws IOException {
        this.root = Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("文件存储根目录：{}", root);
    }

    @Override
    public String store(InputStream inputStream, String relativePath) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return relativePath;
    }

    @Override
    public Path resolve(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的文件路径：" + relativePath);
        }
        return target;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            log.warn("删除文件失败：{}", relativePath, ex);
        }
    }
}
