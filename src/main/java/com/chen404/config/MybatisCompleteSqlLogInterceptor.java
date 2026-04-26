package com.chen404.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * 打印 MyBatis-Plus 最终执行的完整 SQL，便于排查参数绑定与动态 SQL 问题。
 */
public class MybatisCompleteSqlLogInterceptor implements InnerInterceptor {

    private static final Logger SQL_LOG = LoggerFactory.getLogger("sys-sql");

    @Override
    public void beforeQuery(
            Executor executor,
            MappedStatement mappedStatement,
            Object parameter,
            RowBounds rowBounds,
            ResultHandler resultHandler,
            BoundSql boundSql
    ) throws SQLException {
        logSql(mappedStatement, boundSql, parameter);
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement mappedStatement, Object parameter) throws SQLException {
        logSql(mappedStatement, mappedStatement.getBoundSql(parameter), parameter);
    }

    private void logSql(MappedStatement mappedStatement, BoundSql boundSql, Object parameter) {
        try {
            String sqlId = mappedStatement.getId();
            String sql = buildSql(mappedStatement.getConfiguration(), boundSql);
            SQL_LOG.info("[SQL-PREVIEW] sqlId={} parameter={} sql={}", getSimpleSqlId(sqlId), parameter, sql);
        } catch (Exception e) {
            SQL_LOG.warn("打印完整 SQL 失败: {}", e.getMessage(), e);
        }
    }

    private String buildSql(Configuration configuration, BoundSql boundSql) {
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        String sql = boundSql.getSql().replaceAll("[\\s]+", " ").trim();
        if (CollectionUtils.isEmpty(parameterMappings) || parameterObject == null) {
            return sql;
        }

        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            return sql.replaceFirst("\\?", Matcher.quoteReplacement(formatParameterValue(parameterObject)));
        }

        MetaObject metaObject = configuration.newMetaObject(parameterObject);
        for (ParameterMapping parameterMapping : parameterMappings) {
            String propertyName = parameterMapping.getProperty();
            Object value;
            if (metaObject.hasGetter(propertyName)) {
                value = metaObject.getValue(propertyName);
            } else if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else {
                value = "缺失";
            }
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(formatParameterValue(value)));
        }
        return sql;
    }

    private String formatParameterValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String stringValue) {
            return "'" + stringValue.replace("'", "''") + "'";
        }
        if (value instanceof Date dateValue) {
            DateFormat formatter = DateFormat.getDateTimeInstance(
                    DateFormat.DEFAULT,
                    DateFormat.DEFAULT,
                    Locale.CHINA
            );
            return "'" + formatter.format(dateValue) + "'";
        }
        return value.toString();
    }

    private String getSimpleSqlId(String fullSqlId) {
        if (fullSqlId == null) {
            return "unknown";
        }
        int lastDot = fullSqlId.lastIndexOf('.');
        if (lastDot > 0) {
            int secondLastDot = fullSqlId.lastIndexOf('.', lastDot - 1);
            if (secondLastDot > 0) {
                return fullSqlId.substring(secondLastDot + 1);
            }
        }
        return fullSqlId;
    }
}
