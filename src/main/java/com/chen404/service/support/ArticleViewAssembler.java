package com.chen404.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.converter.ArticleViewConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborsVO;
import com.chen404.domain.dto.ArticleTagVO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserArticleFavorite;
import com.chen404.domain.entity.UserArticleLike;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.mapper.TagMapper;
import com.chen404.mapper.UserArticleFavoriteMapper;
import com.chen404.mapper.UserArticleLikeMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.service.AccessService;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.service.SysFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 将文章查询结果组装成视图；关联数据、访问票据和用户权限均不再写回持久化实体。 */
@Component
@RequiredArgsConstructor
public class ArticleViewAssembler {
    private final ArticleViewConverter converter;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserArticleLikeMapper likeMapper;
    private final UserArticleFavoriteMapper favoriteMapper;
    private final SysFileService fileService;
    private final ProtectedFileAccessService fileAccess;
    private final UserAccessProfileSupport userProfiles;
    private final AccessService accessService;

    public PageResult<ArticleListItemVO> toPage(Page<Article> page, Long requesterId, boolean interactions) {
        return new PageResult<>(toList(page.getRecords(), requesterId, interactions),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 批量关联查询仅随当前结果集发生，空页不发起关联查询。 */
    public List<ArticleListItemVO> toList(List<Article> articles, Long requesterId, boolean interactions) {
        if (articles == null || articles.isEmpty()) {
            return List.of();
        }
        List<ArticleListItemVO> views = converter.toListItemVOList(articles);
        decorate(articles, views, requesterId, interactions);
        return views;
    }

    public ArticleDetailVO toDetail(Article article, Long requesterId) {
        ArticleDetailVO view = converter.toDetailVO(article);
        decorate(List.of(article), List.of(view), requesterId, true);
        view.setContent(fileAccess.issueContentUrls(article.getContent(), SysFile.RefType.ARTICLE_CONTENT, article.getId()));
        view.setCanComment(accessService.canCommentArticle(requesterId, article));
        return view;
    }

    public ArticleNeighborsVO toNeighbors(Article previous, Article next) {
        ArticleNeighborsVO view = new ArticleNeighborsVO();
        view.setPrev(converter.toNeighborVO(previous));
        view.setNext(converter.toNeighborVO(next));
        return view;
    }

    private void decorate(List<Article> articles, List<? extends ArticleListItemVO> views,
                          Long requesterId, boolean interactions) {
        Set<Long> authorIds = ids(articles, Article::getAuthorId);
        Set<Long> categoryIds = ids(articles, Article::getCategoryId);
        Set<Long> fileIds = ids(articles, Article::getCoverFileId);
        Map<Long, User> authors = authorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        authors.values().forEach(userProfiles::applyDisplayAvatar);
        Map<Long, Category> categories = categoryIds.isEmpty() ? Map.of() : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<Long, SysFile> files = fileIds.isEmpty() ? Map.of() : fileService.findByIds(fileIds).stream()
                .collect(Collectors.toMap(SysFile::getId, Function.identity()));
        Map<Long, List<ArticleTagVO>> tags = tagsByArticleIds(ids(articles, Article::getId));
        for (int index = 0; index < articles.size(); index++) {
            Article row = articles.get(index);
            ArticleListItemVO view = views.get(index);
            view.setAuthor(converter.toAuthorVO(row.getAuthorId() == null ? null : authors.get(row.getAuthorId())));
            view.setCategory(converter.toCategoryVO(row.getCategoryId() == null ? null : categories.get(row.getCategoryId())));
            view.setTags(tags.getOrDefault(row.getId(), List.of()));
            SysFile cover = row.getCoverFileId() == null ? null : files.get(row.getCoverFileId());
            String coverUrl = cover != null && StringUtils.hasText(cover.getFileUrl()) ? cover.getFileUrl() : row.getCoverImage();
            view.setCoverImage(fileAccess.issueUrlForReference(coverUrl, SysFile.RefType.ARTICLE_COVER, row.getId()));
            boolean canManage = accessService.canManageArticle(requesterId, row);
            view.setCanEdit(canManage);
            view.setCanDelete(canManage);
        }
        if (interactions) {
            fillInteractions(views, requesterId);
        }
    }

    /** 列表、详情和归档共用标签筛选及结构转换。 */
    public Map<Long, List<ArticleTagVO>> tagsByArticleIds(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        List<ArticleTag> relations = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getArticleId, articleIds));
        Set<Long> tagIds = ids(relations, ArticleTag::getTagId);
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ArticleTagVO> tags = tagMapper.selectBatchIds(tagIds).stream()
                .filter(tag -> Objects.equals(tag.getStatus(), 1) && !Objects.equals(tag.getDeleted(), 1))
                .collect(Collectors.toMap(Tag::getId, converter::toTagVO));
        Map<Long, List<ArticleTagVO>> result = new HashMap<>();
        for (ArticleTag relation : relations) {
            ArticleTagVO tag = tags.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getArticleId(), ignored -> new ArrayList<>()).add(tag);
            }
        }
        return result;
    }

    private void fillInteractions(List<? extends ArticleListItemVO> views, Long requesterId) {
        Set<Long> articleIds = ids(views, ArticleListItemVO::getId);
        Set<Long> liked = requesterId == null ? Set.of() : ids(likeMapper.selectList(new LambdaQueryWrapper<UserArticleLike>()
                .eq(UserArticleLike::getUserId, requesterId).in(UserArticleLike::getArticleId, articleIds)), UserArticleLike::getArticleId);
        Set<Long> favorited = requesterId == null ? Set.of() : ids(favoriteMapper.selectList(new LambdaQueryWrapper<UserArticleFavorite>()
                .eq(UserArticleFavorite::getUserId, requesterId).in(UserArticleFavorite::getArticleId, articleIds)), UserArticleFavorite::getArticleId);
        for (ArticleListItemVO view : views) {
            view.setLiked(liked.contains(view.getId()));
            view.setFavorited(favorited.contains(view.getId()));
        }
    }

    private <T> Set<Long> ids(Collection<T> rows, Function<T, Long> field) {
        return rows.stream().map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
