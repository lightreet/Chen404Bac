package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Category;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.CategoryMapper;
import com.chen404.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private static final int STATUS_ENABLED = 1;
    private static final int DEFAULT_SORT_ORDER = 0;

    @Override
    public List<Category> getAllCategories() {
        return baseMapper.selectAllActive();
    }

    @Override
    public Page<Category> getAdminCategoryPage(int page, int size) {
        Page<Category> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId);
        return baseMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Category createCategory(Category category) {
        if (category.getStatus() == null) {
            category.setStatus(STATUS_ENABLED);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(DEFAULT_SORT_ORDER);
        }
        if (!StringUtils.hasText(category.getSlug())) {
            category.setSlug(category.getName() != null ? category.getName() : "");
        }
        save(category);
        return category;
    }

    @Override
    public Category updateCategory(Long id, Category category) {
        Category existing = getById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("分类不存在");
        }
        if (StringUtils.hasText(category.getName())) {
            existing.setName(category.getName());
        }
        if (category.getSlug() != null) {
            existing.setSlug(category.getSlug());
        }
        if (category.getDescription() != null) {
            existing.setDescription(category.getDescription());
        }
        if (category.getIcon() != null) {
            existing.setIcon(category.getIcon());
        }
        if (category.getSortOrder() != null) {
            existing.setSortOrder(category.getSortOrder());
        }
        if (category.getStatus() != null) {
            existing.setStatus(category.getStatus());
        }
        updateById(existing);
        return existing;
    }

    @Override
    public void deleteCategory(Long id) {
        if (!removeById(id)) {
            throw new ResourceNotFoundException("分类不存在或已删除");
        }
    }
}
