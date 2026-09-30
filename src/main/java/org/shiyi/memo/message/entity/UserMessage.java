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
    /** NOTICE / FILE_BLOCKED / MEMO_REMOVED / FRIEND_REQUEST */
    private String type;
    private Long refMemoId;
    private Long refFileId;
    /** FRIEND_REQUEST 时的申请人 */
    private Long refUserId;
    /** FRIEND_REQUEST 的处理状态：PENDING / ACCEPTED / REJECTED */
    private String requestStatus;
    private LocalDateTime readAt;
    private String senderName;
    private LocalDateTime createdAt;
}
