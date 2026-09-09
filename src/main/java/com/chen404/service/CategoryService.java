package com.chen404.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.entity.Category;

import java.util.List;

/** 分类用例接口，持久化操作只通过明确的分类业务方法调用。 */
public interface CategoryService {

    /** 查询单个分类，不存在时返回 null。 */
    Category getCategoryById(Long id);

    List<Category> getAllCategories();

    Page<Category> getAdminCategoryPage(int page, int size);

    Category createCategory(Category category);

    Category updateCategory(Long id, Category category);

    void deleteCategory(Long id);
}
