package org.shiyi.memo.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.shiyi.memo.user.entity.AppUser;

import java.util.List;
import java.util.Map;

public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 用一条 SQL 原子累加已用空间，避免并发上传时算错。
     */
    @Update("UPDATE app_user SET storage_used = GREATEST(storage_used + #{delta}, 0) WHERE id = #{userId}")
    int addStorageUsed(@Param("userId") long userId, @Param("delta") long delta);

    @Select("SELECT level_id AS levelId, COUNT(*) AS cnt FROM app_user GROUP BY level_id")
    List<Map<String, Object>> countByLevel();
}
