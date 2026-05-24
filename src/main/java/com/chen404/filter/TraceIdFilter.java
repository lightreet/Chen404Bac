package com.chen404.filter;

import com.chen404.util.WebRequestUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceID过滤器
 * 为每个请求生成唯一的TraceID，用于链路追踪
 * 优先级最高，确保在其他过滤器之前执行
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String CLIENT_IP_KEY = "clientIp";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.debug("TraceIdFilter 初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // 优先从请求头获取TraceId（用于分布式场景）
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);

            // 如果没有则生成新的
            if (traceId == null || traceId.isEmpty()) {
                traceId = generateTraceId();
            }

            // 放入MDC，后续日志会自动带上traceId
            MDC.put(TRACE_ID_KEY, traceId);

            // 获取客户端IP
            String clientIp = WebRequestUtil.getClientIp(httpRequest);
            MDC.put(CLIENT_IP_KEY, clientIp);

            // 继续过滤器链
            chain.doFilter(request, response);

        } finally {
            // 清除MDC，防止内存泄漏
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(CLIENT_IP_KEY);
        }
    }

    @Override
    public void destroy() {
        log.debug("TraceIdFilter 销毁");
    }

    /**
     * 生成TraceId
     * 格式: 时间戳(8位) + UUID前8位 = 16位
     */
    private String generateTraceId() {
        String timeHex = Long.toHexString(System.currentTimeMillis() / 1000);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return timeHex + uuid;
    }

}
