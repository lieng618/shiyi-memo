package org.shiyi.memo.backup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备份相关的对外结构。清单是备份文件里 manifest.json 的落盘格式，
 * 字段只增不改，老备份要永远能被新版本导入。
 */
public final class BackupDtos {

    private BackupDtos() {
    }

    /** 导入接口的返回：各环节处理了多少条。相同笔记不导入，计入 skippedMemos */
    public record ImportResult(int version, int memoCount, int attachmentCount,
                               int skippedMemos, int skippedAttachments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BackupManifest(int version, String app, LocalDateTime exportedAt,
                                 List<ManifestMemo> memos) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ManifestMemo(Long id, String title, String contentHtml, Boolean pinned,
                                   List<String> tags, LocalDateTime createdAt, LocalDateTime updatedAt,
                                   List<ManifestFile> attachments) {
        }

        /**
         * @param path 压缩包内附件的条目路径；被屏蔽的附件没有文件内容，为 null
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ManifestFile(Long id, String name, String mime, long size,
                                   String path, Boolean blocked) {
        }
    }
}
