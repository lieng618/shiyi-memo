package org.shiyi.memo.backup;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.backup.dto.BackupDtos;
import org.shiyi.memo.security.AuthContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 个人数据备份：GET 打包下载，POST 上传恢复。
 * 挂在 /api/** 下，由 AuthInterceptor 统一鉴权，只能备份 / 恢复自己的数据。
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BackupService backupService;

    @GetMapping
    public void export(HttpServletResponse response) throws IOException {
        long userId = AuthContext.requireUserId();
        String filename = "shiyi-backup-" + STAMP.format(LocalDateTime.now()) + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString());
        backupService.writeBackup(userId, response.getOutputStream());
    }

    @PostMapping
    public BackupDtos.ImportResult importBackup(@RequestParam("file") MultipartFile file) {
        return backupService.importBackup(AuthContext.requireUserId(), file);
    }
}
