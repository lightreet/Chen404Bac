package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.CategoryConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CategoryVO;
import com.chen404.domain.entity.Category;
import com.chen404.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端分类控制器，聚合后台分类列表等管理视角接口。
 */
@Tag(name = "分类管理", description = "管理员分类列表与后续后台分类能力")
@RestController
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final CategoryConverter categoryConverter;

    public AdminCategoryController(CategoryService categoryService, CategoryConverter categoryConverter) {
        this.categoryService = categoryService;
        this.categoryConverter = categoryConverter;
    }

    @Operation(summary = "分页获取分类", description = "仅管理员")
    @RequireAdmin
    @GetMapping
    public Result<PageResult<CategoryVO>> pageCategories(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Category> result = categoryService.getAdminCategoryPage(page, size);
        return Result.success(new PageResult<>(
                categoryConverter.toVOList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        ));
    }
}
