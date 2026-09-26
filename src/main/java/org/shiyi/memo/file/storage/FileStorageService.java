package org.shiyi.memo.file.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 文件存储抽象：现在是本地磁盘，将来想换 MinIO / OSS 只需要换一个实现类。
 */
public interface FileStorageService {

    /**
     * 保存文件。
     *
     * @param relativePath 相对存储根目录的路径，例如 12/2026/09/uuid.jpg
     * @return 实际写入的相对路径
     */
    String store(InputStream inputStream, String relativePath) throws IOException;

    /** 解析成真实路径，越界（../）会抛异常 */
    Path resolve(String relativePath);

    void delete(String relativePath);
}
