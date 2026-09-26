package org.shiyi.memo.tag;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.tag.dto.TagDtos;
import org.shiyi.memo.tag.entity.Tag;
import org.shiyi.memo.tag.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TagService {

    /** 自动创建标签时按顺序循环取色 */
    private static final String[] PALETTE = {
            "#4f7cff", "#12b886", "#f59f00", "#e8590c", "#9c36b5",
            "#0ca678", "#e03131", "#1c7ed6", "#7048e8", "#2b8a3e"
    };

    private final TagMapper tagMapper;

    public List<TagDtos.TagDto> list(long userId) {
        return tagMapper.listWithCount(userId).stream()
                .map(row -> new TagDtos.TagDto(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("name"),
                        (String) row.get("color"),
                        ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    public Tag requireOwned(long userId, long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || !tag.getUserId().equals(userId)) {
            throw ApiException.notFound("标签不存在");
        }
        return tag;
    }

    @Transactional
    public Tag create(long userId, String name, String color) {
        String clean = cleanName(name);
        Tag existing = findByName(userId, clean);
        if (existing != null) {
            if (color != null && !color.isBlank() && !color.equals(existing.getColor())) {
                existing.setColor(color);
                tagMapper.updateById(existing);
            }
            return existing;
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(clean);
        tag.setColor(color == null || color.isBlank() ? palette(clean) : color);
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 把标签名列表（来自前端输入）转成标签 id，不存在就自动创建。
     */
    @Transactional
    public List<Tag> resolveByNames(long userId, Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                unique.add(cleanName(name));
            }
        }
        List<Tag> result = new ArrayList<>(unique.size());
        for (String name : unique) {
            result.add(create(userId, name, null));
        }
        return result;
    }

    @Transactional
    public TagDtos.TagDto update(long userId, long tagId, String name, String color) {
        Tag tag = requireOwned(userId, tagId);
        if (name != null && !name.isBlank()) {
            String clean = cleanName(name);
            Tag sameName = findByName(userId, clean);
            if (sameName != null && !sameName.getId().equals(tagId)) {
                throw ApiException.badRequest("已存在同名标签");
            }
            tag.setName(clean);
        }
        if (color != null && !color.isBlank()) {
            tag.setColor(color);
        }
        tagMapper.updateById(tag);
        return new TagDtos.TagDto(tag.getId(), tag.getName(), tag.getColor(), null);
    }

    @Transactional
    public void delete(long userId, long tagId) {
        Tag tag = requireOwned(userId, tagId);
        tagMapper.unlinkTag(tag.getId());
        tagMapper.deleteById(tag.getId());
    }

    /** 记录详情里要展示标签，按 id 批量查 */
    public Map<Long, Tag> findByIds(long userId, Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Tag> map = new LinkedHashMap<>();
        for (Tag tag : tagMapper.selectBatchIds(tagIds)) {
            if (tag.getUserId().equals(userId)) {
                map.put(tag.getId(), tag);
            }
        }
        return map;
    }

    private Tag findByName(long userId, String name) {
        return tagMapper.selectOne(Wrappers.lambdaQuery(Tag.class)
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, name)
                .last("limit 1"));
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw ApiException.badRequest("标签名不能为空");
        }
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static String palette(String seed) {
        return PALETTE[Math.floorMod(seed.hashCode(), PALETTE.length)];
    }
}
