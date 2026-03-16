package com.chen404.interceptor;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * SQL性能拦截器
 * 记录执行的完整SQL语句（替换占位符为实际参数）和执行时间
 */
@Slf4j
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
    @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlPerformanceInterceptor implements Interceptor {

    /**
     * 慢查询阈值（毫秒）
     */
    private static final long SLOW_QUERY_THRESHOLD = 1000;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        long startTime = System.currentTimeMillis();

        StatementHandler statementHandler = PluginUtils.realTarget(target);
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        // 获取SQL和参数
        BoundSql boundSql = statementHandler.getBoundSql();
        Configuration configuration = mappedStatement.getConfiguration();

        // 获取带实际参数的完整SQL
        String completeSql = getCompleteSql(boundSql, configuration);

        // 执行SQL
        Object result = invocation.proceed();

        // 计算执行时间
        long duration = System.currentTimeMillis() - startTime;

        // 获取影响行数
        int rows = getEffectedRows(result, invocation.getMethod().getName());

        // 简化的SQL ID（去掉包名）
        String simpleSqlId = getSimpleSqlId(mappedStatement.getId());

        // 构建日志：一行展示完整SQL
        String logMsg = String.format("[SQL] %s | %dms | rows=%d | %s",
                simpleSqlId, duration, rows, completeSql);

        // 根据耗时选择日志级别
        if (duration > SLOW_QUERY_THRESHOLD) {
            log.warn("[SLOW-SQL] {}", logMsg);
        } else {
            log.info(logMsg);
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
     * 获取完整SQL（替换?为实际参数）
     */
    private String getCompleteSql(BoundSql boundSql, Configuration configuration) {
        String sql = boundSql.getSql();
        sql = sql.replaceAll("[\\s]+", " ").trim();

        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();

        if (CollectionUtils.isEmpty(parameterMappings) || parameterObject == null) {
            return sql;
        }

        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        List<String> parameters = new ArrayList<>();

        for (ParameterMapping parameterMapping : parameterMappings) {
            if (parameterMapping.getMode() == ParameterMode.OUT) {
                continue;
            }

            String propertyName = parameterMapping.getProperty();
            Object value;

            // 从additionalParameters获取
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else {
                // 从parameterObject获取
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                if (metaObject.hasGetter(propertyName)) {
                    value = metaObject.getValue(propertyName);
                } else {
                    value = parameterObject;
                }
            }

            parameters.add(formatParameterValue(value));
        }

        // 替换?为实际参数值
        return replacePlaceholders(sql, parameters);
    }

    /**
     * 格式化参数值为SQL字符串
     */
    private String formatParameterValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        // 字符串类型
        if (value instanceof String) {
            String str = (String) value;
            // 敏感字段脱敏
            if (str.length() > 100) {
                return "'" + str.substring(0, 50) + "...'";
            }
            // 转义单引号
            str = str.replace("'", "''");
            return "'" + str + "'";
        }

        // 日期类型
        if (value instanceof Date) {
            return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value) + "'";
        }

        // 布尔类型
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }

        // 数字类型
        return value.toString();
    }

    /**
     * 替换SQL中的?占位符为实际参数
     */
    private String replacePlaceholders(String sql, List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return sql;
        }

        StringBuilder result = new StringBuilder();
        int paramIndex = 0;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && paramIndex < parameters.size()) {
                result.append(parameters.get(paramIndex++));
            } else {
                result.append(c);
            }
        }

        return result.toString();
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
