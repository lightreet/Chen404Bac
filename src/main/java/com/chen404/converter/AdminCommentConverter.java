package com.chen404.converter;

import com.chen404.domain.dto.AdminCommentVO;
import com.chen404.domain.entity.Comment;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 管理端评论视图转换器。
 */
@Mapper(componentModel = "spring")
public interface AdminCommentConverter {

    /**
     * 将评论实体转换为管理端视图，文章和回复上下文由服务层批量补充。
     *
     * @param comment 评论实体
     * @return 管理端评论视图
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "articleId", source = "articleId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "rootId", source = "rootId")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "authorName", source = "authorName")
    @Mapping(target = "authorEmail", source = "authorEmail")
    @Mapping(target = "authorWebsite", source = "authorWebsite")
    @Mapping(target = "authorAvatar", source = "authorAvatar")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "ip", source = "ip")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "userAgent", source = "userAgent")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "isAdmin", source = "isAdmin")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    AdminCommentVO toVO(Comment comment);
}
