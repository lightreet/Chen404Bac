package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    List<Category> getAllCategories();

    Category createCategory(Category category);

    Category updateCategory(Long id, Category category);

    void deleteCategory(Long id);
}
