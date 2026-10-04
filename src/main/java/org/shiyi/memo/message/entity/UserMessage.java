package org.shiyi.memo.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String content;
    /** 后台富文本通知的正文（wangEditor HTML），系统消息为 null */
    private String contentHtml;
    /** NOTICE / FILE_BLOCKED / MEMO_REMOVED */
    private String type;
    private Long refMemoId;
    private Long refFileId;
    private LocalDateTime readAt;
    private String senderName;
    private LocalDateTime createdAt;
}
