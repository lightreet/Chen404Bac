package com.chen404.converter;

import com.chen404.domain.dto.BannerVO;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.Comment;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 首页相关视图转换器。
 */
@Mapper(componentModel = "spring")
public interface HomeViewConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "subtitle", source = "subtitle")
    @Mapping(target = "image", source = "image")
    @Mapping(target = "link", source = "link")
    @Mapping(target = "target", source = "target")
    @Mapping(target = "position", source = "position")
    @Mapping(target = "backgroundColor", source = "backgroundColor")
    @Mapping(target = "textColor", source = "textColor")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "startTime", source = "startTime")
    @Mapping(target = "endTime", source = "endTime")
    BannerVO toBannerVO(Banner banner);

    List<BannerVO> toBannerVOList(List<Banner> banners);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "articleId", source = "articleId")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "authorName", source = "authorName")
    @Mapping(target = "authorAvatar", source = "authorAvatar")
    @Mapping(target = "createTime", source = "createTime")
    RecentCommentVO toRecentCommentVO(Comment comment);

    List<RecentCommentVO> toRecentCommentVOList(List<Comment> comments);
}
