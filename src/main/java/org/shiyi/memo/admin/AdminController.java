package org.shiyi.memo.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.admin.dto.AdminDtos;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.security.AdminContext;
import org.shiyi.memo.user.dto.UserDtos;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 后台控制台接口。全部要求后台登录态（见 AdminInterceptor），普通用户拿不到。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final FileService fileService;

    // ---------------- 概览 ----------------

    @GetMapping("/stats")
    public AdminDtos.StatsDto stats() {
        return adminService.stats();
    }

    // ---------------- 用户 ----------------

    @GetMapping("/users")
    public PageResult<AdminDtos.AdminUserDto> users(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return adminService.listUsers(keyword, status, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    /** 修改用户：昵称 / 等级 / 启用状态 */
    @PutMapping("/users/{id}")
    public AdminDtos.AdminUserDto updateUser(@PathVariable long id,
                                             @Valid @RequestBody AdminDtos.UpdateUserRequest request) {
        return adminService.updateUser(id, request);
    }

    // ---------------- 等级 ----------------

    @GetMapping("/levels")
    public List<UserDtos.LevelDto> levels() {
        return adminService.listLevels();
    }

    @PostMapping("/levels")
    public UserDtos.LevelDto createLevel(@Valid @RequestBody UserDtos.LevelRequest request) {
        return adminService.createLevel(request);
    }

    @PutMapping("/levels/{id}")
    public UserDtos.LevelDto updateLevel(@PathVariable long id, @Valid @RequestBody UserDtos.LevelRequest request) {
        return adminService.updateLevel(id, request);
    }

    @DeleteMapping("/levels/{id}")
    public void deleteLevel(@PathVariable long id) {
        adminService.deleteLevel(id);
    }

    // ---------------- 笔记 ----------------

    @GetMapping("/memos")
    public PageResult<AdminDtos.AdminMemoDto> memos(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return adminService.listMemos(keyword, userId, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    @GetMapping("/memos/{id}")
    public AdminDtos.AdminMemoDetail memoDetail(@PathVariable long id) {
        return adminService.memoDetail(id);
    }

    /** 删除违规笔记，可附一句原因（会以站内信发给作者） */
    @DeleteMapping("/memos/{id}")
    public void deleteMemo(@PathVariable long id, @RequestParam(required = false) String reason) {
        adminService.deleteMemo(id, AdminContext.get(), reason);
    }

    // ---------------- 文件 ----------------

    /** 后台上传信件附件：图片插正文、其他文件插附件链接；归属系统，不占任何用户配额 */
    @PostMapping("/files/upload")
    public FileDtos.FileDto uploadFile(@RequestParam("file") MultipartFile file) {
        return fileService.uploadSystemFile(file);
    }

    @GetMapping("/files")
    public PageResult<AdminDtos.AdminFileDto> files(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) Boolean blocked,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return adminService.listFiles(keyword, userId, blocked, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    /** 屏蔽违规文件（会自动给文件所有者发站内信） */
    @PostMapping("/files/{id}/block")
    public AdminDtos.AdminFileDto blockFile(@PathVariable long id,
                                            @RequestBody(required = false) AdminDtos.BlockFileRequest request) {
        return adminService.blockFile(id, request == null ? null : request.reason(), AdminContext.get());
    }

    @PostMapping("/files/{id}/unblock")
    public AdminDtos.AdminFileDto unblockFile(@PathVariable long id) {
        return adminService.unblockFile(id);
    }

    /** 后台预览原文件：被屏蔽的文件也能看 */
    @GetMapping("/files/{id}/raw")
    public ResponseEntity<Resource> rawFile(@PathVariable long id) {
        FileAsset asset = fileService.getAny(id);
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(asset))
                .contentLength(asset.getSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(new FileSystemResource(fileService.pathOf(asset)));
    }

    @GetMapping("/files/{id}/thumb")
    public ResponseEntity<Resource> thumbFile(@PathVariable long id,
                                              @RequestParam(defaultValue = "480") Integer w) {
        FileAsset asset = fileService.getAny(id);
        Path thumbnail = fileService.thumbnailOf(asset, w);
        if (thumbnail == null) {
            return rawFile(id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(new FileSystemResource(thumbnail));
    }

    // ---------------- 站内信 ----------------

    @GetMapping("/messages")
    public PageResult<AdminDtos.AdminMessageDto> messages(@RequestParam(required = false) Long userId,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return adminService.listMessages(userId, Math.max(1, page), Math.min(Math.max(1, size), 100));
    }

    /** 给某个用户发站内信 */
    @PostMapping("/messages")
    public void sendMessage(@Valid @RequestBody AdminDtos.SendMessageRequest request) {
        adminService.sendMessage(request, AdminContext.get());
    }

    /** 全服信：发给全体正常状态用户，每人一条 */
    @PostMapping("/messages/broadcast")
    public void broadcastMessage(@Valid @RequestBody AdminDtos.BroadcastMessageRequest request) {
        adminService.broadcast(request, AdminContext.get());
    }

    // ---------------- 欢迎信 ----------------

    @GetMapping("/welcome-letter")
    public AdminDtos.WelcomeLetterDto welcomeLetter() {
        return adminService.welcomeLetter();
    }

    /** 保存欢迎信配置；开启后新注册的用户会收到一封站内信 */
    @PutMapping("/welcome-letter")
    public AdminDtos.WelcomeLetterDto updateWelcomeLetter(
            @Valid @RequestBody AdminDtos.WelcomeLetterRequest request) {
        return adminService.updateWelcomeLetter(request, AdminContext.get());
    }

    private static MediaType mediaTypeOf(FileAsset asset) {
        try {
            return MediaType.parseMediaType(asset.getMime());
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
