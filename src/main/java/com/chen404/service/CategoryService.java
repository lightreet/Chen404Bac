package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    List<Category> getAllCategories();
}
