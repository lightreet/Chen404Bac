package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 标签Mapper
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 查询所有启用的标签
     */
    @Select("SELECT * FROM tag WHERE status = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<Tag> selectAllActive();

    /**
     * 根据文章ID查询标签列表
     */
    @Select("SELECT t.* FROM tag t " +
            "INNER JOIN article_tag at ON t.id = at.tag_id " +
            "WHERE at.article_id = #{articleId} AND t.status = 1 AND t.deleted = 0")
    List<Tag> selectTagsByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据slug查询标签
     */
    @Select("SELECT * FROM tag WHERE slug = #{slug} AND deleted = 0")
    Tag selectBySlug(@Param("slug") String slug);
}
