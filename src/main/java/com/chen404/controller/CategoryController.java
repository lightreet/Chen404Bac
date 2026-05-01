package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.CategoryConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CategoryVO;
import com.chen404.domain.dto.CreateCategoryCommand;
import com.chen404.domain.dto.UpdateCategoryCommand;
import com.chen404.domain.entity.Category;
import com.chen404.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 分类控制器：公共查询与管理员管理统一归口到分类资源下，
 * 管理员接口继续保留 /admin/categories 路径，但权限改由方法级切面控制。
 */
@Tag(name = "分类", description = "分类列表与管理（创建/更新/删除需管理员）")
@RestController
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryConverter categoryConverter;

    public CategoryController(CategoryService categoryService, CategoryConverter categoryConverter) {
        this.categoryService = categoryService;
        this.categoryConverter = categoryConverter;
    }

    @Operation(summary = "获取所有分类")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> getCategories(
            @RequestParam(defaultValue = "false") boolean withArticleCount) {
        List<Category> categories = categoryService.getAllCategories();
        if (categories == null) {
            categories = Collections.emptyList();
        }
        return Result.success(categoryConverter.toVOList(categories));
    }

    @Operation(summary = "获取分类详情")
    @GetMapping("/categories/{id}")
    public Result<CategoryVO> getCategoryById(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id) {
        Category category = categoryService.getById(id);
        if (category == null) {
            return Result.error(404, "分类不存在");
        }
        return Result.success(categoryConverter.toVO(category));
    }

    @RequireAdmin
    @Operation(summary = "分页获取分类", description = "仅管理员")
    @GetMapping("/admin/categories")
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

    @RequireAdmin
    @Operation(summary = "创建分类", description = "仅管理员")
    @PostMapping("/categories")
    public Result<CategoryVO> createCategory(@Valid @RequestBody CreateCategoryCommand command) {
        Category category = categoryConverter.toEntity(command);
        try {
            Category created = categoryService.createCategory(category);
            return Result.success("创建成功", categoryConverter.toVO(created));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "更新分类", description = "仅管理员")
    @Parameter(name = "id", description = "分类ID", required = true)
    @PutMapping("/categories/{id}")
    public Result<CategoryVO> updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryCommand command) {
        Category category = categoryConverter.toEntity(command);
        try {
            Category updated = categoryService.updateCategory(id, category);
            return Result.success("更新成功", categoryConverter.toVO(updated));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @RequireAdmin
    @Operation(summary = "删除分类", description = "仅管理员，逻辑删除")
    @Parameter(name = "id", description = "分类ID", required = true)
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
