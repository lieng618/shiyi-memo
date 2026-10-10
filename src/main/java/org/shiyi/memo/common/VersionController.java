package org.shiyi.memo.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.security.PublicApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/** 站点版本信息，免登录 */
@Slf4j
@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    /** 没有 build-info.properties 时的兜底值 */
    private static final String UNKNOWN_VERSION = "dev";
    /** 下载目录里安装包的固定文件名 */
    private static final String DEFAULT_DESKTOP_FILE = "shiyi-memo-setup.exe";
    /** electron-builder 生成的更新清单，里面有最新外壳版本 */
    private static final String UPDATE_MANIFEST = "latest.yml";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final AppProperties properties;

    @PublicApi
    @GetMapping
    public VersionDto version() {
        BuildProperties build = buildProperties.getIfAvailable();
        return new VersionDto(
                build == null ? UNKNOWN_VERSION : build.getVersion(),
                build == null ? null : build.getTime(),
                desktopInfo());
    }

    private DesktopDto desktopInfo() {
        AppProperties.Desktop desktop = properties.getDesktop();
        return new DesktopDto(latestDesktopVersion(desktop), downloadUrl(desktop));
    }

    /**
     * 最新外壳版本：默认去下载目录读 latest.yml（发版脚本会连同安装包一起传上去），
     * 这样版本号只在 desktop/package.json 里写一次。配置里显式配了就以配置为准。
     */
    private String latestDesktopVersion(AppProperties.Desktop desktop) {
        String configured = desktop.getLatestVersion();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Path manifest = downloadDir(desktop).resolve(UPDATE_MANIFEST);
        if (!Files.isReadable(manifest)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(manifest)) {
            Map<?, ?> parsed = new Yaml().load(input);
            Object version = parsed == null ? null : parsed.get("version");
            return version == null ? null : String.valueOf(version);
        } catch (Exception ex) {
            log.warn("读取 {} 失败：{}", manifest, ex.getMessage());
            return null;
        }
    }

    /** 没配 download-url 时按站点地址拼，发版只要覆盖同名安装包 */
    private String downloadUrl(AppProperties.Desktop desktop) {
        if (desktop.getDownloadUrl() != null && !desktop.getDownloadUrl().isBlank()) {
            return desktop.getDownloadUrl();
        }
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().replaceAll("/+$", "");
        return base + "/downloads/" + DEFAULT_DESKTOP_FILE;
    }

    private Path downloadDir(AppProperties.Desktop desktop) {
        return Paths.get(desktop.getDownloadDir()).toAbsolutePath().normalize();
    }

    public record VersionDto(String version, Instant buildTime, DesktopDto desktop) {
    }

    public record DesktopDto(String latestVersion, String downloadUrl) {
    }
}
