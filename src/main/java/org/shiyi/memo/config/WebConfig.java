package org.shiyi.memo.config;

import lombok.RequiredArgsConstructor;
import org.shiyi.memo.security.AdminInterceptor;
import org.shiyi.memo.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** Vite 产物文件名里带内容 hash，改了名字就会变，可以放心缓存一年 */
    private static final CacheControl IMMUTABLE_ASSETS =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    /**
     * 名字不带 hash 的静态素材（图标 / 表情包 / 欢迎页配图 / 供应商脚本）：
     * 缓存一天，过期后靠 Last-Modified 走 304，不会白下载一遍。
     */
    private static final CacheControl STABLE_ASSETS =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    /**
     * 外壳 HTML 必须每次校验：里面引用的都是带 hash 的资源，
     * 一旦被启发式缓存住，用户就会停在旧版本上。
     */
    private static final CacheControl REVALIDATE_HTML = CacheControl.noCache();

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
     * 静态资源的缓存策略。默认情况下 Spring Boot 不给静态资源发 Cache-Control，
     * 浏览器只能靠启发式规则猜，结果是每次刷新都要为每个 chunk 发一次条件请求。
     * 这里按「文件名是否带 hash」分档：带 hash 的硬缓存，外壳 HTML 每次校验。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(IMMUTABLE_ASSETS)
                // 带 hash 的文件不需要 Last-Modified，省掉一次条件请求
                .setUseLastModified(false);

        registry.addResourceHandler("/lib/**")
                .addResourceLocations("classpath:/static/lib/")
                .setCacheControl(STABLE_ASSETS);
        registry.addResourceHandler("/emoji/**")
                .addResourceLocations("classpath:/static/emoji/")
                .setCacheControl(STABLE_ASSETS);
        registry.addResourceHandler("/welcome/**")
                .addResourceLocations("classpath:/static/welcome/")
                .setCacheControl(STABLE_ASSETS);
        registry.addResourceHandler("/favicon.png", "/apple-touch-icon.png", "/logo.png")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(STABLE_ASSETS);

        registry.addResourceHandler("/index.html", "/admin.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(REVALIDATE_HTML);
    }
}
