package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章Mapper
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 分页查询文章列表（包含作者和分类信息）
     */
    Page<Article> selectArticlePage(Page<Article> page, @Param("status") Integer status);

    /**
     * 根据ID查询文章详情（包含作者和分类信息）
     */
    Article selectArticleById(@Param("id") Long id);

    /**
     * 获取热门文章
     */
    @Select("SELECT id, title, view_count FROM article " +
            "WHERE status = 1 AND visibility = 0 AND deleted = 0 " +
            "ORDER BY view_count DESC LIMIT #{limit}")
    List<Article> selectHotArticles(@Param("limit") Integer limit);

    /**
     * 获取推荐文章
     */
    @Select("SELECT id, title, cover_image, summary FROM article " +
            "WHERE status = 1 AND visibility = 0 AND is_recommend = 1 AND deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<Article> selectRecommendArticles(@Param("limit") Integer limit);

    /**
     * 增加浏览量
     */
    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 增加点赞数
     */
    @Update("UPDATE article SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);

    /**
     * 获取站点统计
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM article WHERE deleted = 0 AND status = 1 AND visibility = 0) as articleCount, " +
            "(SELECT COUNT(*) FROM category WHERE deleted = 0 AND status = 1) as categoryCount, " +
            "(SELECT COUNT(*) FROM tag WHERE deleted = 0 AND status = 1) as tagCount, " +
            "(SELECT COUNT(*) FROM comment WHERE deleted = 0 AND status = 1) as commentCount, " +
            "(SELECT COALESCE(SUM(view_count), 0) FROM article WHERE deleted = 0 AND status = 1 AND visibility = 0) as viewCount")
    java.util.Map<String, Object> selectSiteStats();
}
