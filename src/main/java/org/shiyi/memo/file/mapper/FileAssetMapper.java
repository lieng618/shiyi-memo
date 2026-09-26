package org.shiyi.memo.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.shiyi.memo.file.entity.FileAsset;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FileAssetMapper extends BaseMapper<FileAsset> {

    @Select("<script>SELECT memo_id AS memoId, COUNT(*) AS cnt FROM file_asset WHERE memo_id IN "
            + "<foreach collection='memoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY memo_id</script>")
    List<Map<String, Object>> countByMemoIds(@Param("memoIds") Collection<Long> memoIds);

    /**
     * 每条记录挑一张最早上传的图片当封面。
     */
    @Select("<script>SELECT memo_id AS memoId, MIN(id) AS fileId FROM file_asset "
            + "WHERE kind = 'image' AND blocked = 0 AND memo_id IN "
            + "<foreach collection='memoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY memo_id</script>")
    List<Map<String, Object>> selectCoverImages(@Param("memoIds") Collection<Long> memoIds);

    /**
     * 按用户统计附件数量；blockedOnly = true 时只统计被屏蔽的。
     */
    @Select("<script>SELECT user_id AS userId, COUNT(*) AS cnt FROM file_asset WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "<if test='blockedOnly'>AND blocked = 1</if> "
            + "GROUP BY user_id</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") Collection<Long> userIds,
                                             @Param("blockedOnly") boolean blockedOnly);
}
