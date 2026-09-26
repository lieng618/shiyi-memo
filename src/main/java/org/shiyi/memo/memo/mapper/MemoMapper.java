package org.shiyi.memo.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.shiyi.memo.memo.entity.Memo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MemoMapper extends BaseMapper<Memo> {

    @Select("<script>SELECT user_id AS userId, COUNT(*) AS cnt FROM memo WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") Collection<Long> userIds);
}
