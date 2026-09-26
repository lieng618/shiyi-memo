package org.shiyi.memo.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("memo")
public class Memo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    /** Markdown 原文 */
    private String contentMd;
    private Boolean pinned;
    private LocalDateTime pinnedAt;
    private String shareToken;
    private Boolean shareEnabled;
    private LocalDateTime sharedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
