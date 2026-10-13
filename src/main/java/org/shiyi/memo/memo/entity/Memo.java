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
    /** 富文本 HTML（wangEditor） */
    private String contentHtml;
    /** 去标签后的纯文本，搜索和摘要都读它 */
    private String contentText;
    /** 列表摘要，写入时算好，列表页直接用 */
    private String excerpt;
    private Boolean pinned;
    private LocalDateTime pinnedAt;
    private String shareToken;
    private Boolean shareEnabled;
    private LocalDateTime sharedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
