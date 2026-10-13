package org.shiyi.memo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 前端打包进 jar 之后，直接访问 /notes/12 这类深链接会落到后端：
 * 备忘录回退到 index.html，后台控制台 /admin 回退到 admin.html。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shouldForward(request)) {
            String target = isAdminPath(request.getRequestURI()) ? "/admin.html" : "/index.html";
            // 回退的是外壳 HTML，必须每次校验，否则用户会被卡在旧版本上
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            request.getRequestDispatcher(target).forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAdminPath(String path) {
        return path.equals("/admin") || path.startsWith("/admin/");
    }

    private boolean shouldForward(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.equals("/api")) {
            return false;
        }
        if (isAdminPath(path)) {
            return true;
        }
        if (path.equals("/index.html")) {
            return false;
        }
        // 带扩展名的都当静态资源处理（.js/.css/.png …）
        if (path.lastIndexOf('.') > path.lastIndexOf('/')) {
            return false;
        }
        try {
            return getServletContext().getResource(path) == null;
        } catch (Exception ex) {
            return false;
        }
    }
}
