package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.UserArticleLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserArticleLikeMapper extends BaseMapper<UserArticleLike> {

    /**
     * 删除指定用户与文章的点赞关系。
     */
    @Delete("DELETE FROM user_article_like WHERE user_id = #{userId} AND article_id = #{articleId}")
    int deleteLike(@Param("userId") Long userId, @Param("articleId") Long articleId);

    /**
     * 原子插入点赞关系；并发请求已插入时返回 0。
     */
    @Insert("INSERT IGNORE INTO user_article_like (user_id, article_id, create_time) "
            + "VALUES (#{userId}, #{articleId}, NOW())")
    int insertLikeIfAbsent(@Param("userId") Long userId, @Param("articleId") Long articleId);
}
