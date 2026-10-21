package org.shiyi.memo.memo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.shiyi.memo.memo.entity.Memo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MemoMapper extends BaseMapper<Memo> {

    /** 锁住笔记行，串行化同一篇笔记的编辑租约申请。 */
    @Select("SELECT id FROM memo WHERE id = #{memoId} AND user_id = #{userId} FOR UPDATE")
    Long lockOwned(@Param("userId") long userId, @Param("memoId") long memoId);

    /**
     * 只有版本、编辑会话和未过期租约都匹配时才写入正文。
     * 任何一项不匹配都返回 0，由服务层转换成 409。
     */
    @Update("""
            <script>
            UPDATE memo
            <set>
                <if test="memo.contentHtml != null">
                    content_html = #{memo.contentHtml},
                    content_text = #{memo.contentText},
                    excerpt = #{memo.excerpt},
                </if>
                title = #{memo.title},
                <if test="memo.pinned != null">
                    pinned = #{memo.pinned},
                    pinned_at = #{memo.pinnedAt},
                </if>
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            </set>
            WHERE id = #{memo.id}
              AND user_id = #{userId}
              AND version = #{expectedVersion}
              AND EXISTS (
                  SELECT 1
                  FROM memo_edit_lease lease
                  WHERE lease.memo_id = memo.id
                    AND lease.session_id = #{sessionId}
                    AND lease.expires_at &gt; #{now}
              )
            </script>
            """)
    int updateWithLease(@Param("memo") Memo memo,
                        @Param("userId") long userId,
                        @Param("expectedVersion") long expectedVersion,
                        @Param("sessionId") String sessionId,
                        @Param("now") LocalDateTime now);

    @Select("<script>SELECT user_id AS userId, COUNT(*) AS cnt FROM memo WHERE user_id IN "
            + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY user_id</script>")
    List<Map<String, Object>> countByUserIds(@Param("userIds") Collection<Long> userIds);
}
