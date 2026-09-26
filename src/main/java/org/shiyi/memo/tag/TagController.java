package org.shiyi.memo.tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.security.AuthContext;
import org.shiyi.memo.tag.dto.TagDtos;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public List<TagDtos.TagDto> list() {
        return tagService.list(AuthContext.requireUserId());
    }

    @PostMapping
    public TagDtos.TagDto create(@Valid @RequestBody TagDtos.TagRequest request) {
        var tag = tagService.create(AuthContext.requireUserId(), request.name(), request.color());
        return new TagDtos.TagDto(tag.getId(), tag.getName(), tag.getColor(), 0L);
    }

    @PutMapping("/{id}")
    public TagDtos.TagDto update(@PathVariable long id, @Valid @RequestBody TagDtos.TagRequest request) {
        return tagService.update(AuthContext.requireUserId(), id, request.name(), request.color());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        tagService.delete(AuthContext.requireUserId(), id);
    }
}
