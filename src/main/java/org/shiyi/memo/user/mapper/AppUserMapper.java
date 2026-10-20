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

    /** 让该用户已经签发的令牌全部失效 */
    @Update("UPDATE app_user SET token_version = token_version + 1 WHERE id = #{userId}")
    int bumpTokenVersion(@Param("userId") long userId);

    /** 改密码和吊销旧令牌放在同一条 SQL 里，避免中间状态 */
    @Update("UPDATE app_user SET password_hash = #{hash}, token_version = token_version + 1 WHERE id = #{userId}")
    int updatePasswordHashAndBumpVersion(@Param("userId") long userId, @Param("hash") String hash);

    @Select("SELECT level_id AS levelId, COUNT(*) AS cnt FROM app_user GROUP BY level_id")
    List<Map<String, Object>> countByLevel();
}
