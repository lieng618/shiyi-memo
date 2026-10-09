package org.shiyi.memo.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.shiyi.memo.message.entity.UserMessage;

import java.util.Collection;
import java.util.Map;
import java.util.List;

public interface UserMessageMapper extends BaseMapper<UserMessage> {

    /** 未读数只算已送达的：定时信没到点不该让角标变红 */
    @Select("SELECT COUNT(*) FROM user_message "
            + "WHERE user_id = #{userId} AND read_at IS NULL AND type != 'FRIEND_REQUEST' "
            + "AND (send_at IS NULL OR send_at <= NOW())")
    long countUnread(@Param("userId") long userId);

    /** ids 为空表示全部已读 */
    @Update("<script>UPDATE user_message SET read_at = NOW() "
            + "WHERE user_id = #{userId} AND read_at IS NULL "
            + "AND type != 'FRIEND_REQUEST' "
            + "<if test='ids != null and ids.size() > 0'>AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>"
            + "</script>")
    int markRead(@Param("userId") long userId, @Param("ids") Collection<Long> ids);

    @Select("<script>SELECT user_id AS userId, COUNT(*) AS cnt FROM user_message "
            + "WHERE type != 'FRIEND_REQUEST' AND user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * 站内信附件授权：收到的消息正文（内嵌图片 / 附件链接）里引用了这个文件即可读；
     * 还没到点的定时信不算「收到」，不授权。
     * 路径两端的 / 保证按完整 id 匹配，不会误命中 /api/files/12/ 里的 1。
     */
    @Select("SELECT COUNT(*) FROM user_message "
            + "WHERE user_id = #{userId} AND content_html LIKE CONCAT('%/api/files/', #{fileId}, '/%') "
            + "AND (send_at IS NULL OR send_at <= NOW())")
    long countReadableFileMessage(@Param("userId") long userId, @Param("fileId") long fileId);
}
