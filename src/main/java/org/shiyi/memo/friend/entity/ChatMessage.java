package org.shiyi.memo.friend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;
    private Long recipientId;
    private String content;
    /** 0 = 没送达（发的时候对方已经把我删了），只有发送方自己看得见 */
    private Boolean deliverable = true;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
