package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.entity.Category;
import com.chen404.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 分类控制器：GET 公开，增删改需管理员（由 @RequireAdmin 切面校验）
 */
@Tag(name = "分类", description = "分类列表与管理（创建/更新/删除需管理员）")
@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Operation(summary = "获取所有分类")
    @GetMapping("")
    public Result<List<Category>> getCategories(
            @RequestParam(defaultValue = "false") boolean withArticleCount) {
        List<Category> categories = categoryService.getAllCategories();
        if (categories == null) {
            categories = Collections.emptyList();
        }
        return Result.success(categories);
    }

    @Operation(summary = "获取分类详情")
    @GetMapping("/{id}")
    public Result<Category> getCategoryById(@Parameter(description = "分类ID", required = true) @PathVariable Long id) {
        Category category = categoryService.getById(id);
        if (category == null) {
            return Result.error(404, "分类不存在");
        }
        return Result.success(category);
    }

    @RequireAdmin
    @Operation(summary = "创建分类", description = "仅管理员")
    @PostMapping("")
    public Result<Category> createCategory(@RequestBody Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            return Result.error(400, "分类名称不能为空");
        }
        try {
            Category created = categoryService.createCategory(category);
            return Result.success("创建成功", created);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "更新分类", description = "仅管理员")
    @Parameter(name = "id", description = "分类ID", required = true)
    @PutMapping("/{id}")
    public Result<Category> updateCategory(@PathVariable Long id, @RequestBody Category category) {
        try {
            Category updated = categoryService.updateCategory(id, category);
            return Result.success("更新成功", updated);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "删除分类", description = "仅管理员，逻辑删除")
    @Parameter(name = "id", description = "分类ID", required = true)
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
