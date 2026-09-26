package org.shiyi.memo.file.dto;

import org.shiyi.memo.file.entity.FileAsset;

import java.time.LocalDateTime;

public final class FileDtos {

    private FileDtos() {
    }

    /**
     * @param url      原文件地址，支持 Range（视频可直接拖动进度条）
     * @param thumbUrl 图片缩略图地址，非图片为 null
     */
    public record FileDto(Long id, Long memoId, String originalName, String ext, String mime, Long size,
                          String kind, Integer width, Integer height, String url, String thumbUrl,
                          Boolean blocked, String blockedReason,
                          LocalDateTime createdAt) {

        public static FileDto from(FileAsset asset) {
            boolean image = "image".equals(asset.getKind());
            boolean blocked = Boolean.TRUE.equals(asset.getBlocked());
            return new FileDto(asset.getId(), asset.getMemoId(), asset.getOriginalName(), asset.getExt(),
                    asset.getMime(), asset.getSize(), asset.getKind(), asset.getWidth(), asset.getHeight(),
                    blocked ? null : "/api/files/" + asset.getId() + "/raw",
                    image && !blocked ? "/api/files/" + asset.getId() + "/thumb" : null,
                    blocked, asset.getBlockedReason(),
                    asset.getCreatedAt());
        }
    }
}
