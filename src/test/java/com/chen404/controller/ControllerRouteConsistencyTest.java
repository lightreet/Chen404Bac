package com.chen404.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ControllerRouteConsistencyTest {

    @Test
    void categoryControllersShouldDeclareClassLevelPrefixes() {
        assertArrayEquals(new String[]{"/categories"},
                CategoryController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/admin/categories"},
                AdminCategoryController.class.getAnnotation(RequestMapping.class).value());
    }
}
