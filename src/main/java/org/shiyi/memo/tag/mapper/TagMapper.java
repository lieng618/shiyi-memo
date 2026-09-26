package org.shiyi.memo.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.shiyi.memo.tag.entity.Tag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TagMapper extends BaseMapper<Tag> {

    @Insert("INSERT IGNORE INTO memo_tag (memo_id, tag_id) VALUES (#{memoId}, #{tagId})")
    int link(@Param("memoId") long memoId, @Param("tagId") long tagId);

    @Delete("DELETE FROM memo_tag WHERE memo_id = #{memoId}")
    int unlinkAll(@Param("memoId") long memoId);

    @Delete("DELETE FROM memo_tag WHERE tag_id = #{tagId}")
    int unlinkTag(@Param("tagId") long tagId);

    @Select("SELECT tag_id FROM memo_tag WHERE memo_id = #{memoId}")
    List<Long> selectTagIds(@Param("memoId") long memoId);

    @Select("<script>SELECT memo_id AS memoId, tag_id AS tagId FROM memo_tag WHERE memo_id IN "
            + "<foreach collection='memoIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectLinks(@Param("memoIds") Collection<Long> memoIds);

    @Select("SELECT tag_id AS tagId, COUNT(*) AS cnt FROM memo_tag GROUP BY tag_id")
    List<Map<String, Object>> countAllLinks();

    @Select("<script>SELECT DISTINCT memo_id AS memoId FROM memo_tag WHERE tag_id IN "
            + "<foreach collection='tagIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectMemoIdsByTags(@Param("tagIds") Collection<Long> tagIds);

    @Select("""
            SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(mt.memo_id) AS cnt
            FROM tag t
            LEFT JOIN memo_tag mt ON mt.tag_id = t.id
            WHERE t.user_id = #{userId}
            GROUP BY t.id, t.name, t.color
            ORDER BY cnt DESC, t.id ASC
            """)
    List<Map<String, Object>> listWithCount(@Param("userId") long userId);
}
