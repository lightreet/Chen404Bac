package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.CommentGuestToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentGuestTokenMapper extends BaseMapper<CommentGuestToken> {

    @Select("SELECT * FROM comment_guest_token WHERE comment_id = #{commentId} LIMIT 1")
    CommentGuestToken selectByCommentId(Long commentId);
}

