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

    @Select("SELECT COUNT(*) FROM user_message WHERE user_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") long userId);

    /** ids 为空表示全部已读 */
    @Update("<script>UPDATE user_message SET read_at = NOW() "
            + "WHERE user_id = #{userId} AND read_at IS NULL "
            + "<if test='ids != null and ids.size() > 0'>AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></if>"
            + "</script>")
    int markRead(@Param("userId") long userId, @Param("ids") Collection<Long> ids);

    @Select("<script>SELECT user_id AS userId, COUNT(*) AS cnt FROM user_message WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") Collection<Long> userIds);
}
