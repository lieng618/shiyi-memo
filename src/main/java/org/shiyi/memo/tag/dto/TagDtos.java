package org.shiyi.memo.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.shiyi.memo.tag.entity.Tag;

public final class TagDtos {

    private TagDtos() {
    }

    public record TagDto(Long id, String name, String color, Long memoCount) {

        public static TagDto from(Tag tag, Long memoCount) {
            return new TagDto(tag.getId(), tag.getName(), tag.getColor(), memoCount);
        }
    }

    public record TagRequest(@NotBlank @Size(max = 64) String name, @Size(max = 16) String color) {
    }
}
