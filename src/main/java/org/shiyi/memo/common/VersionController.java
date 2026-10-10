package org.shiyi.memo.common;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.security.PublicApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 站点版本信息，免登录 */
@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    /** 没有 build-info.properties 时的兜底值 */
    private static final String UNKNOWN_VERSION = "dev";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final AppProperties properties;

    @PublicApi
    @GetMapping
    public VersionDto version() {
        BuildProperties build = buildProperties.getIfAvailable();
        return new VersionDto(
                build == null ? UNKNOWN_VERSION : build.getVersion(),
                build == null ? null : build.getTime(),
                new DesktopDto(properties.getDesktop().getDownloadUrl()));
    }

    public record VersionDto(String version, Instant buildTime, DesktopDto desktop) {
    }

    public record DesktopDto(String downloadUrl) {
    }
}
