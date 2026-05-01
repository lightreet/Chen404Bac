package com.chen404.converter;

import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.UpdateArticleCommand;
import com.chen404.domain.entity.Article;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 文章命令对象与实体转换器。
 */
@Mapper(componentModel = "spring")
public interface ArticleCommandConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "isTop", source = "isTop")
    @Mapping(target = "isRecommend", source = "isRecommend")
    @Mapping(target = "isOriginal", source = "isOriginal")
    @Mapping(target = "originalUrl", source = "originalUrl")
    @Mapping(target = "password", source = "password")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "commentPolicy", source = "commentPolicy")
    @Mapping(target = "tagIds", source = "tagIds")
    @Mapping(target = "tagNames", source = "tagNames")
    Article toEntity(CreateArticleCommand command);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "isTop", source = "isTop")
    @Mapping(target = "isRecommend", source = "isRecommend")
    @Mapping(target = "isOriginal", source = "isOriginal")
    @Mapping(target = "originalUrl", source = "originalUrl")
    @Mapping(target = "password", source = "password")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "commentPolicy", source = "commentPolicy")
    @Mapping(target = "tagIds", source = "tagIds")
    @Mapping(target = "tagNames", source = "tagNames")
    Article toEntity(UpdateArticleCommand command);
}
