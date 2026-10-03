package org.shiyi.memo.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_asset")
public class FileAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long memoId;
    private String originalName;
    private String ext;
    private String mime;
    private Long size;
    private String kind;
    private Integer width;
    private Integer height;
    private String storagePath;
    /** 文件内容 SHA-256，同账号去重用 */
    private String contentHash;
    /** 后台屏蔽：1 表示用户不能再查看 / 下载 */
    private Boolean blocked;
    private String blockedReason;
    private LocalDateTime blockedAt;
    private LocalDateTime createdAt;
}
