package com.chen404.converter;

import com.chen404.domain.dto.ArticleAuthorVO;
import com.chen404.domain.dto.ArticleCategoryVO;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborVO;
import com.chen404.domain.dto.ArticleTagVO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 文章实体与视图对象转换器。
 */
@Mapper(componentModel = "spring")
public interface ArticleViewConverter {

    @Named("toArticleListItemVO")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "viewCount", source = "viewCount")
    @Mapping(target = "commentCount", source = "commentCount")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "isTop", source = "isTop")
    @Mapping(target = "isRecommend", source = "isRecommend")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "commentPolicy", source = "commentPolicy")
    @Mapping(target = "publishTime", source = "publishTime")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "canEdit", source = "canEdit")
    @Mapping(target = "canDelete", source = "canDelete")
    @Mapping(target = "liked", source = "liked")
    @Mapping(target = "favorited", source = "favorited")
    ArticleListItemVO toListItemVO(Article article);

    @IterableMapping(qualifiedByName = "toArticleListItemVO")
    List<ArticleListItemVO> toListItemVOList(List<Article> articles);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "contentHtml", source = "contentHtml")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "viewCount", source = "viewCount")
    @Mapping(target = "commentCount", source = "commentCount")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "isTop", source = "isTop")
    @Mapping(target = "isRecommend", source = "isRecommend")
    @Mapping(target = "isOriginal", source = "isOriginal")
    @Mapping(target = "originalUrl", source = "originalUrl")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "commentPolicy", source = "commentPolicy")
    @Mapping(target = "publishTime", source = "publishTime")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "canEdit", source = "canEdit")
    @Mapping(target = "canDelete", source = "canDelete")
    @Mapping(target = "canComment", source = "canComment")
    @Mapping(target = "liked", source = "liked")
    @Mapping(target = "favorited", source = "favorited")
    ArticleDetailVO toDetailVO(Article article);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    ArticleNeighborVO toNeighborVO(Article article);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "nickname", source = "nickname")
    @Mapping(target = "avatar", source = "avatar")
    @Mapping(target = "bio", source = "bio")
    ArticleAuthorVO toAuthorVO(User user);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "icon", source = "icon")
    ArticleCategoryVO toCategoryVO(Category category);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "color", source = "color")
    ArticleTagVO toTagVO(Tag tag);

    List<ArticleTagVO> toTagVOList(List<Tag> tags);
}
