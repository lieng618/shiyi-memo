package org.shiyi.memo.file;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.security.AuthContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<String> INLINE_KINDS = Set.of("image", "video", "audio");

    private final FileService fileService;

    /** 上传附件，memoId 可选（先上传后关联也行） */
    @PostMapping
    public FileDtos.FileDto upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "memoId", required = false) Long memoId) {
        return fileService.upload(AuthContext.requireUserId(), file, memoId);
    }

    /** 我的文件列表 */
    @GetMapping
    public PageResult<FileDtos.FileDto> list(@RequestParam(required = false) Long memoId,
                                             @RequestParam(required = false) String kind,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "30") long size) {
        return fileService.list(AuthContext.requireUserId(), memoId, kind,
                Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/{id}")
    public FileDtos.FileDto detail(@PathVariable long id) {
        // 聊天的对方点开文件卡片时也要取元数据，所以按「可读」而非「所有」校验
        return FileDtos.FileDto.from(fileService.requireReadable(AuthContext.requireUserId(), id));
    }

    /**
     * 原文件。图片 / 视频 / 音频走 inline 展示，浏览器可以直接播放；
     * 返回 Resource 时 Spring MVC 会自动处理 Range 请求，视频能拖动进度条。
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable long id) {
        FileAsset asset = fileService.requireReadable(AuthContext.requireUserId(), id);
        return resourceResponse(asset, fileService.pathOf(asset), true);
    }

    /** 图片缩略图；生成失败（例如 HEIC）时退回原图。聊天对方也能取，按可读校验 */
    @GetMapping("/{id}/thumb")
    public ResponseEntity<Resource> thumb(@PathVariable long id,
                                          @RequestParam(defaultValue = "480") Integer w) {
        FileAsset asset = fileService.requireReadable(AuthContext.requireUserId(), id);
        if (Boolean.TRUE.equals(asset.getBlocked())) {
            throw ApiException.forbidden("该文件已被管理员屏蔽");
        }
        Path thumbnail = fileService.thumbnailOf(asset, w);
        if (thumbnail == null) {
            return resourceResponse(asset, fileService.pathOf(asset), true);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .body(new FileSystemResource(thumbnail));
    }

    /** 强制下载（带原始文件名） */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        FileAsset asset = fileService.requireReadable(AuthContext.requireUserId(), id);
        return resourceResponse(asset, fileService.pathOf(asset), false);
    }

    @PostMapping("/{id}/attach")
    public void attach(@PathVariable long id, @RequestParam(required = false) Long memoId) {
        fileService.attach(AuthContext.requireUserId(), id, memoId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        fileService.delete(AuthContext.requireUserId(), id);
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
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(path));
    }
}
