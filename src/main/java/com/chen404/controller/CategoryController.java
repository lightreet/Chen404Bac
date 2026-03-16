package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Category;
import com.chen404.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 获取所有分类
     * @param withArticleCount 是否包含文章数量，默认为false
     */
    @GetMapping("")
    public Result<List<Category>> getCategories(
            @RequestParam(defaultValue = "false") boolean withArticleCount) {
        List<Category> categories = categoryService.getAllCategories();
        // 如果为空，返回空列表而不是null
        if (categories == null) {
            categories = Collections.emptyList();
        }
        return Result.success(categories);
    }
}
