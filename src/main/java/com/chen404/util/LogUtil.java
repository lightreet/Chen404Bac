package com.chen404.util;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 日志工具类
 * 提供统一的日志记录方法，支持方法执行时间统计、异常记录等
 */
@Slf4j
public class LogUtil {

    /**
     * 记录方法执行时间
     *
     * @param operation 操作名称
     * @param supplier  要执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    public static <T> T time(String operation, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");

        try {
            log.info("[START] {} | traceId={}", operation, traceId);
            T result = supplier.get();
            long duration = System.currentTimeMillis() - start;
            log.info("[END] {} | {}ms | traceId={}", operation, duration, traceId);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[ERROR] {} | {}ms | traceId={} | error={}",
                    operation, duration, traceId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 记录方法执行时间（无返回值）
     *
     * @param operation 操作名称
     * @param runnable  要执行的操作
     */
    public static void time(String operation, Runnable runnable) {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");

        try {
            log.info("[START] {} | traceId={}", operation, traceId);
            runnable.run();
            long duration = System.currentTimeMillis() - start;
            log.info("[END] {} | {}ms | traceId={}", operation, duration, traceId);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[ERROR] {} | {}ms | traceId={} | error={}",
                    operation, duration, traceId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 记录业务操作日志
     *
     * @param module    模块名称
     * @param action    操作类型
     * @param userId    用户ID
     * @param details   详细信息
     */
    public static void biz(String module, String action, Long userId, Object details) {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("module", module);
        logMap.put("action", action);
        logMap.put("userId", userId);
        logMap.put("traceId", MDC.get("traceId"));
        if (details != null) {
            logMap.put("details", details);
        }

        log.info("[BIZ] {}", JSON.toJSONString(logMap));
    }

    /**
     * 记录业务操作日志（简化版）
     *
     * @param module  模块名称
     * @param action  操作类型
     * @param details 详细信息
     */
    public static void biz(String module, String action, Object details) {
        biz(module, action, null, details);
    }

    /**
     * 记录慢操作警告
     *
     * @param operation 操作名称
     * @param threshold 阈值（毫秒）
     * @param actual    实际耗时（毫秒）
     * @param details   详细信息
     */
    public static void slow(String operation, long threshold, long actual, Object details) {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("operation", operation);
        logMap.put("threshold", threshold + "ms");
        logMap.put("actual", actual + "ms");
        logMap.put("exceed", (actual - threshold) + "ms");
        logMap.put("traceId", MDC.get("traceId"));
        if (details != null) {
            logMap.put("details", details);
        }

        log.warn("[SLOW] {}", JSON.toJSONString(logMap));
    }

    /**
     * 记录数据变更日志
     *
     * @param entity    实体名称
     * @param action    操作类型（CREATE/UPDATE/DELETE）
     * @param id        数据ID
     * @param oldValue  旧值
     * @param newValue  新值
     */
    public static void dataChange(String entity, String action, Object id, Object oldValue, Object newValue) {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("entity", entity);
        logMap.put("action", action);
        logMap.put("id", id);
        logMap.put("traceId", MDC.get("traceId"));

        if (oldValue != null) {
            logMap.put("old", maskSensitiveData(oldValue));
        }
        if (newValue != null) {
            logMap.put("new", maskSensitiveData(newValue));
        }

        log.info("[DATA] {}", JSON.toJSONString(logMap));
    }

    /**
     * 记录安全相关日志
     *
     * @param type     安全事件类型
     * @param userId   用户ID
     * @param details  详细信息
     */
    public static void security(String type, Long userId, Object details) {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("type", type);
        logMap.put("userId", userId);
        logMap.put("ip", MDC.get("clientIp"));
        logMap.put("traceId", MDC.get("traceId"));
        if (details != null) {
            logMap.put("details", details);
        }

        log.warn("[SECURITY] {}", JSON.toJSONString(logMap));
    }

    /**
     * 脱敏敏感数据
     */
    private static Object maskSensitiveData(Object data) {
        if (data == null) {
            return null;
        }
        // 将对象转为JSON字符串进行简单脱敏处理
        String json = JSON.toJSONString(data);
        return json.replaceAll("(\"password\s*\"\s*:\s*\")([^\"]*)(\")", "$1******$3")
                   .replaceAll("(\"token\s*\"\s*:\s*\")([^\"]{10})[^\"]*(\")", "$1$2...$3");
    }

    /**
     * 获取当前TraceId
     */
    public static String getTraceId() {
        return MDC.get("traceId");
    }

    /**
     * 设置TraceId（用于异步场景）
     */
    public static void setTraceId(String traceId) {
        MDC.put("traceId", traceId);
    }

    /**
     * 清除TraceId
     */
    public static void clearTraceId() {
        MDC.remove("traceId");
    }
}
