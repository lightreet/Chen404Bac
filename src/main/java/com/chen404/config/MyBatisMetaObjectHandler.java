package com.chen404.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.chen404.util.CurrentUserUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus ???? create_time?update_time ??????
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        fillDefaultDeleted(metaObject);
        fillAuditUser(metaObject, "createdBy");
        fillAuditUser(metaObject, "updatedBy");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        fillAuditUser(metaObject, "updatedBy");
    }

    private void fillDefaultDeleted(MetaObject metaObject) {
        if (!metaObject.hasSetter("deleted")) {
            return;
        }
        Object currentValue = getFieldValByName("deleted", metaObject);
        if (currentValue == null) {
            setFieldValByName("deleted", 0, metaObject);
        }
    }

    private void fillAuditUser(MetaObject metaObject, String fieldName) {
        if (!metaObject.hasSetter(fieldName)) {
            return;
        }
        Object currentValue = getFieldValByName(fieldName, metaObject);
        if (currentValue != null) {
            return;
        }

        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }

        Class<?> setterType = metaObject.getSetterType(fieldName);
        if (setterType == Long.class || setterType == long.class) {
            setFieldValByName(fieldName, currentUserId, metaObject);
        } else if (setterType == String.class) {
            setFieldValByName(fieldName, String.valueOf(currentUserId), metaObject);
        }
    }
}
