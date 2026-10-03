package org.shiyi.memo.friend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.shiyi.memo.friend.entity.ChatMessage;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查看方与文件所有者的会话里，是否真的发过含该文件标记的消息。
     * 只算送达成功的，且晚于查看方的清空时间——清空过的消息不再授予文件访问权。
     */
    @Select("""
            SELECT COUNT(*) FROM chat_message cm
            LEFT JOIN chat_clear cc
              ON cc.user_id = #{viewerId} AND cc.friend_id = #{ownerId}
            WHERE cm.content LIKE CONCAT('[[file:', #{fileId}, ':%')
              AND cm.deliverable = 1
              AND ((cm.sender_id = #{viewerId} AND cm.recipient_id = #{ownerId})
                OR (cm.sender_id = #{ownerId} AND cm.recipient_id = #{viewerId}))
              AND (cc.cleared_at IS NULL OR cm.created_at > cc.cleared_at)
            """)
    long countVisibleFileMessage(@Param("viewerId") long viewerId,
                                 @Param("ownerId") long ownerId,
                                 @Param("fileId") long fileId);
}
