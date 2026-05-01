package com.chen404.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerSwaggerAnnotationTest {

    private static final List<Class<?>> CONTROLLERS_REQUIRING_SWAGGER = List.of(
            HomeController.class,
            SiteController.class,
            TagController.class
    );

    @Test
    void shouldDeclareClassLevelTag() {
        for (Class<?> controllerClass : CONTROLLERS_REQUIRING_SWAGGER) {
            Tag tag = controllerClass.getAnnotation(Tag.class);
            assertNotNull(tag, () -> controllerClass.getSimpleName() + " 缺少 @Tag 注解");
            assertFalse(tag.name().isBlank(), () -> controllerClass.getSimpleName() + " 的 @Tag.name 不能为空");
            assertFalse(tag.description().isBlank(), () -> controllerClass.getSimpleName() + " 的 @Tag.description 不能为空");
        }
    }

    @Test
    void shouldDeclareOperationForEachRequestHandler() {
        for (Class<?> controllerClass : CONTROLLERS_REQUIRING_SWAGGER) {
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (!isRequestHandler(method)) {
                    continue;
                }
                Operation operation = method.getAnnotation(Operation.class);
                assertNotNull(operation, () -> controllerClass.getSimpleName() + "." + method.getName() + " 缺少 @Operation 注解");
                assertFalse(operation.summary().isBlank(), () -> controllerClass.getSimpleName() + "." + method.getName() + " 的 summary 不能为空");
                assertTrue(operation.description() == null || !operation.description().isBlank(),
                        () -> controllerClass.getSimpleName() + "." + method.getName() + " 的 description 不能为空字符串");
            }
        }
    }

    private boolean isRequestHandler(Method method) {
        return hasAnyAnnotation(method,
                GetMapping.class,
                PostMapping.class,
                PutMapping.class,
                DeleteMapping.class,
                PatchMapping.class,
                RequestMapping.class);
    }

    @SafeVarargs
    private final boolean hasAnyAnnotation(Method method, Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (method.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }
}
