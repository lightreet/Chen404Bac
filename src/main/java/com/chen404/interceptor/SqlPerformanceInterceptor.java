package com.chen404.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * SQL性能拦截器
 * 记录 SQL 执行耗时和影响行数；完整 SQL 由 MybatisCompleteSqlLogInterceptor 负责。
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
    @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlPerformanceInterceptor implements Interceptor {

    private static final Logger SQL_LOG = LoggerFactory.getLogger("sys-sql");

    /** 慢查询阈值（毫秒） */
    private static final long SLOW_QUERY_THRESHOLD = 1000;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        long startTime = System.currentTimeMillis();

        StatementHandler statementHandler = PluginUtils.realTarget(target);
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        // 执行SQL
        Object result = invocation.proceed();

        // 计算执行时间
        long duration = System.currentTimeMillis() - startTime;

        // 获取影响行数
        int rows = getEffectedRows(result, invocation.getMethod().getName());

        String simpleSqlId = getSimpleSqlId(mappedStatement.getId());

        if (duration > SLOW_QUERY_THRESHOLD) {
            SQL_LOG.warn("[SLOW-SQL] sqlId={} duration={}ms rows={}", simpleSqlId, duration, rows);
        } else {
            SQL_LOG.info("[SQL-STAT] sqlId={} duration={}ms rows={}", simpleSqlId, duration, rows);
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
    }

    /**
     * 获取简化的SQL ID
     */
    private String getSimpleSqlId(String fullId) {
        if (fullId == null) {
            return "unknown";
        }
        int lastDot = fullId.lastIndexOf('.');
        if (lastDot > 0) {
            // 获取类名.方法名
            int secondLastDot = fullId.lastIndexOf('.', lastDot - 1);
            if (secondLastDot > 0) {
                return fullId.substring(secondLastDot + 1);
            }
        }
        return fullId;
    }

    /**
     * 获取影响行数
     */
    private int getEffectedRows(Object result, String methodName) {
        try {
            if ("update".equals(methodName)) {
                return (Integer) result;
            } else if ("query".equals(methodName)) {
                if (result instanceof List) {
                    return ((List<?>) result).size();
                }
                return result == null ? 0 : 1;
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }
}
