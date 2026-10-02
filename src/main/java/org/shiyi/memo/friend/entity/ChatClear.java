package org.shiyi.memo.friend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 清空聊天记录的标记：clearedAt 之前的消息对 user 不可见 */
@Data
@TableName("chat_clear")
public class ChatClear {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long friendId;
    private LocalDateTime clearedAt;
}
