package org.shiyi.memo.config;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.security.AdminInterceptor;
import org.shiyi.memo.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final AppProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/api/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getSecurity().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 桌面端安装包直接从磁盘发，省得为几个静态文件单独配 nginx。
     * 走 Spring 的资源处理器，Range 请求、ETag、Last-Modified 都是现成的。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(properties.getDesktop().getDownloadDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
