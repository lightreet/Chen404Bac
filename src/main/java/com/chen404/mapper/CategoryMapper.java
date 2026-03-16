package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 分类Mapper
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 查询所有启用的分类
     */
    @Select("SELECT * FROM category WHERE status = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<Category> selectAllActive();

    /**
     * 根据slug查询分类
     */
    @Select("SELECT * FROM category WHERE slug = #{slug} AND deleted = 0")
    Category selectBySlug(@Param("slug") String slug);

    /**
     * 更新文章数量
     */
    @Update("UPDATE category SET article_count = " +
            "(SELECT COUNT(*) FROM article WHERE category_id = #{categoryId} AND status = 1 AND deleted = 0) " +
            "WHERE id = #{categoryId}")
    int updateArticleCount(@Param("categoryId") Long categoryId);
}
