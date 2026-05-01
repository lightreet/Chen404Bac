package com.chen404.converter;

import com.chen404.domain.dto.CommentVO;
import com.chen404.domain.entity.Comment;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评论视图转换器。
 */
@Mapper(componentModel = "spring")
public interface CommentConverter {

    @Named("toCommentVOShallow")
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
    @Mapping(target = "isAdmin", source = "isAdmin")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "replyToUserId", source = "replyToUserId")
    @Mapping(target = "replyToAuthorName", source = "replyToAuthorName")
    @Mapping(target = "guestDeleteKey", source = "guestDeleteKey")
    @Mapping(target = "likedByMe", source = "likedByMe")
    CommentVO toShallowVO(Comment comment);

    default CommentVO toVO(Comment comment) {
        if (comment == null) {
            return null;
        }
        CommentVO vo = toShallowVO(comment);
        List<Comment> children = comment.getChildren();
        if (children == null || children.isEmpty()) {
            vo.setChildren(Collections.emptyList());
        } else {
            vo.setChildren(children.stream().map(this::toVO).collect(Collectors.toList()));
        }
        return vo;
    }

    default List<CommentVO> toVOList(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }
        return comments.stream().map(this::toVO).collect(Collectors.toList());
    }
}
