package com.chen404.service;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Article;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 固化公开业务接口和持久化模型的边界，防止通用写入能力或视图字段重新混入。 */
class ServiceBoundaryTest {
    @Test
    void businessInterfacesMustNotExposeGenericCrudOrQueryWrappers() {
        for (Class<?> service : List.of(ArticleService.class, UserService.class, CategoryService.class,
                TagService.class, BannerService.class, SysFileService.class, FileReferenceService.class,
                UserTrustRequestService.class)) {
            assertFalse(IService.class.isAssignableFrom(service), service.getName());
            for (var method : service.getMethods()) {
                assertTrue(Arrays.stream(method.getParameterTypes()).noneMatch(Wrapper.class::isAssignableFrom),
                        service.getSimpleName() + "." + method.getName());
            }
        }
    }

    @Test
    void articleUseCasesMustNotAcceptOrReturnPersistenceEntity() {
        for (var method : ArticleService.class.getMethods()) {
            assertFalse(Arrays.asList(method.getParameterTypes()).contains(Article.class));
            assertFalse(method.getGenericReturnType().getTypeName().contains(Article.class.getName()));
        }
        for (var field : Article.class.getDeclaredFields()) {
            TableField mapping = field.getAnnotation(TableField.class);
            assertTrue(mapping == null || mapping.exist(), field.getName());
        }
    }
}
