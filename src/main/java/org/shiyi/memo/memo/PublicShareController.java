package org.shiyi.memo.memo;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.memo.dto.MemoDtos;
import org.shiyi.memo.security.PublicApi;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@PublicApi
@RestController
@RequestMapping("/api/public/memos")
@RequiredArgsConstructor
public class PublicShareController {

    private static final Set<String> INLINE_KINDS = Set.of("image", "video", "audio");

    private final MemoService memoService;
    private final FileService fileService;

    @GetMapping("/{token}")
    public MemoDtos.PublicMemoDetail detail(@PathVariable String token) {
        return memoService.publicDetail(token);
    }

    @GetMapping("/{token}/owner-avatar")
    public ResponseEntity<Resource> ownerAvatar(@PathVariable String token) {
        FileAsset asset = memoService.publicOwnerAvatar(token);
        Path thumbnail = fileService.thumbnailOf(asset, 160);
        if (thumbnail == null) {
            return resourceResponse(asset, fileService.pathOf(asset), true);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(thumbnail));
    }

    @GetMapping("/{token}/files/{fileId}/raw")
    public ResponseEntity<Resource> raw(@PathVariable String token, @PathVariable long fileId) {
        FileAsset asset = publicAsset(token, fileId);
        return resourceResponse(asset, fileService.pathOf(asset), true);
    }

    @GetMapping("/{token}/files/{fileId}/thumb")
    public ResponseEntity<Resource> thumb(@PathVariable String token, @PathVariable long fileId,
                                          @RequestParam(defaultValue = "480") Integer w) {
        FileAsset asset = publicAsset(token, fileId);
        Path thumbnail = fileService.thumbnailOf(asset, w);
        if (thumbnail == null) {
            return resourceResponse(asset, fileService.pathOf(asset), true);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(thumbnail));
    }

    @GetMapping("/{token}/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable String token, @PathVariable long fileId) {
        FileAsset asset = publicAsset(token, fileId);
        return resourceResponse(asset, fileService.pathOf(asset), false);
    }

    private FileAsset publicAsset(String token, long fileId) {
        long memoId = memoService.requirePublicShare(token).getId();
        FileAsset asset = fileService.requirePublicReadable(memoId, fileId);
        if (Boolean.TRUE.equals(asset.getBlocked())) {
            throw ApiException.forbidden("该文件已被管理员屏蔽");
        }
        return asset;
    }

    private ResponseEntity<Resource> resourceResponse(FileAsset asset, Path path, boolean allowInline) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.getMime());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        boolean inline = allowInline
                && (INLINE_KINDS.contains(asset.getKind()) || "application/pdf".equals(mediaType.toString()));
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(asset.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(path));
    }
}
