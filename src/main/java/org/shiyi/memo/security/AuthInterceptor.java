package org.shiyi.memo.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.shiyi.memo.common.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 等异步请求完成时会再次进入派发阶段，此时初始请求已经鉴权过了。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 后台接口由 AdminInterceptor 负责鉴权
        if (request.getRequestURI().startsWith("/api/admin")) {
            return true;
        }
        if (hasAnnotation(handlerMethod, PublicApi.class)) {
            return true;
        }
        AuthUser user = AuthContext.get();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return true;
    }

    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> type) {
        return handlerMethod.hasMethodAnnotation(type)
                || handlerMethod.getBeanType().isAnnotationPresent(type);
    }
}
