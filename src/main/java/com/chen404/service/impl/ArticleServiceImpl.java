package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.dto.ArchiveArticleItem;
import com.chen404.domain.dto.ArchiveMonthVO;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleLikeResult;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserArticleFavorite;
import com.chen404.domain.entity.UserArticleLike;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.mapper.TagMapper;
import com.chen404.mapper.UserArticleFavoriteMapper;
import com.chen404.mapper.UserArticleLikeMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.service.AccessService;
import com.chen404.service.ArticleService;
import com.chen404.service.SysFileService;
import com.chen404.service.TagService;
import com.chen404.service.support.UserAccessProfileSupport;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private static final long LIKE_COOLDOWN_MS = 60_000L;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private ArticleTagMapper articleTagMapper;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private TagService tagService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private UserArticleLikeMapper userArticleLikeMapper;

    @Autowired
    private UserArticleFavoriteMapper userArticleFavoriteMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Override
    public Page<Article> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, String keyword) {
        Page<Article> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        // 公开列表只返回已发布 + 公开可见文章
        wrapper.eq(Article::getStatus, Article.Status.PUBLISHED);
        wrapper.eq(Article::getVisibility, Article.Visibility.PUBLIC);

        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }

        // 标签筛选
        if (tagId != null) {
            wrapper.inSql(Article::getId, "SELECT article_id FROM article_tag WHERE tag_id = " + tagId);
        }

        // 关键词搜索（公开列表：仅匹配标题）
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }

        // 排序：置顶优先，再按「展示时间」倒序（无 publish_time 时用 create_time，避免 NULL 全堆在最前）
        wrapper.last("ORDER BY is_top DESC, COALESCE(publish_time, create_time) DESC, id DESC");

        Page<Article> result = articleMapper.selectPage(pageParam, wrapper);

        // 填充关联数据
        List<Article> records = result.getRecords();
        for (Article article : records) {
            fillArticleRelations(article);
        }

        return result;
    }

    @Override
    public Page<Article> getMyArticlePage(Long userId, Integer page, Integer size, Integer status, String keyword) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getAuthorId, userId);
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Article::getTitle, keyword)
                    .or()
                    .like(Article::getSummary, keyword));
        }
        // 已发布按发布时间、草稿按最近编辑时间，统一「最近在前」
        wrapper.last("ORDER BY COALESCE(publish_time, update_time, create_time) DESC, id DESC");
        Page<Article> result = articleMapper.selectPage(pageParam, wrapper);
        for (Article article : result.getRecords()) {
            fillArticleRelations(article);
            accessService.fillArticlePermissions(article, userId);
        }
        return result;
    }

    @Override
    public Article getArticleById(Long id, boolean incrementView, Long requesterId) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            return null;
        }

        if (!accessService.canViewArticle(requesterId, article)) {
            throw new ForbiddenException("当前文章无权访问");
        }

        // 增加浏览量
        if (incrementView) {
            articleMapper.incrementViewCount(id);
            article.setViewCount(article.getViewCount() + 1);
        }

        // 填充关联数据
        fillArticleRelations(article);
        accessService.fillArticlePermissions(article, requesterId);
        fillArticleInteractionFlags(article, requesterId);

        return article;
    }

    private void fillArticleInteractionFlags(Article article, Long requesterId) {
        if (article == null || requesterId == null) {
            return;
        }
        long likedCount = userArticleLikeMapper.selectCount(new LambdaQueryWrapper<UserArticleLike>()
                .eq(UserArticleLike::getUserId, requesterId)
                .eq(UserArticleLike::getArticleId, article.getId()));
        article.setLiked(likedCount > 0);
        long favCount = userArticleFavoriteMapper.selectCount(new LambdaQueryWrapper<UserArticleFavorite>()
                .eq(UserArticleFavorite::getUserId, requesterId)
                .eq(UserArticleFavorite::getArticleId, article.getId()));
        article.setFavorited(favCount > 0);
    }

    @Override
    public Map<String, Article> getNeighbors(Long articleId, Long requesterId) {
        Article current = articleMapper.selectById(articleId);
        if (current == null || current.getPublishTime() == null || !accessService.canViewArticle(requesterId, current)) {
            return Map.of();
        }
        Map<String, Article> result = new java.util.HashMap<>();

        // 上一篇：发布时间早于当前，取最近一篇
        LambdaQueryWrapper<Article> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.eq(Article::getStatus, Article.Status.PUBLISHED)
                .eq(Article::getVisibility, Article.Visibility.PUBLIC)
                .lt(Article::getPublishTime, current.getPublishTime())
                .orderByDesc(Article::getPublishTime)
                .last("LIMIT 1");
        Article prev = articleMapper.selectOne(prevWrapper);
        if (prev != null) {
            result.put("prev", prev);
        }

        // 下一篇：发布时间晚于当前，取最早一篇
        LambdaQueryWrapper<Article> nextWrapper = new LambdaQueryWrapper<>();
        nextWrapper.eq(Article::getStatus, Article.Status.PUBLISHED)
                .eq(Article::getVisibility, Article.Visibility.PUBLIC)
                .gt(Article::getPublishTime, current.getPublishTime())
                .orderByAsc(Article::getPublishTime)
                .last("LIMIT 1");
        Article next = articleMapper.selectOne(nextWrapper);
        if (next != null) {
            result.put("next", next);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Article createArticle(Article article) {
        User operator = accessService.getUserOrNull(article.getAuthorId());
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.isAdmin(operator) && !accessService.isFriend(operator)) {
            throw new ForbiddenException("仅受信任用户可创建文章");
        }

        // 设置默认值
        article.setViewCount(0);
        article.setLikeCount(0);
        article.setCommentCount(0);
        if (article.getIsTop() == null) {
            article.setIsTop(0);
        }
        if (article.getIsRecommend() == null) {
            article.setIsRecommend(0);
        }
        if (article.getIsOriginal() == null) {
            article.setIsOriginal(1);
        }
        if (article.getVisibility() == null) {
            article.setVisibility(Article.Visibility.PUBLIC);
        }
        if (article.getCommentPolicy() == null) {
            article.setCommentPolicy(Article.CommentPolicy.REGISTERED);
        }
        if (operator == null || !accessService.isAdmin(operator)) {
            article.setIsTop(0);
            article.setIsRecommend(0);
        }

        // 如果是发布状态，设置发布时间
        if (article.getStatus() == Article.Status.PUBLISHED && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 自动生成摘要（如果未填写）
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        articleMapper.insert(article);

        // 解析 tagNames 为 ID 并合并到 tagIds，再保存标签关联
        List<Long> resolvedTagIds = resolveTagIds(article);
        if (!resolvedTagIds.isEmpty()) {
            saveArticleTags(article.getId(), resolvedTagIds);
        }

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }

        // 将文章中引用的文件转为永久状态
        convertArticleFilesToPermanent(article);

        return article;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Article updateArticle(Long id, Article article, Long operatorId) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("文章不存在");
        }
        User operator = accessService.getUserOrNull(operatorId);
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.isAdmin(operator)) {
            throw new ForbiddenException("仅管理员可修改文章");
        }

        article.setId(id);
        article.setAuthorId(existing.getAuthorId());
        article.setViewCount(existing.getViewCount());
        article.setLikeCount(existing.getLikeCount());
        article.setCommentCount(existing.getCommentCount());

        if (article.getVisibility() == null) {
            article.setVisibility(existing.getVisibility() == null ? Article.Visibility.PUBLIC : existing.getVisibility());
        }
        if (article.getCommentPolicy() == null) {
            article.setCommentPolicy(existing.getCommentPolicy() == null
                    ? Article.CommentPolicy.REGISTERED
                    : existing.getCommentPolicy());
        }
        if (operator == null || !accessService.isAdmin(operator)) {
            article.setIsTop(existing.getIsTop());
            article.setIsRecommend(existing.getIsRecommend());
        }

        // 如果从草稿变为发布，设置发布时间
        if (existing.getStatus() == Article.Status.DRAFT && article.getStatus() == Article.Status.PUBLISHED) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 自动生成摘要
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        articleMapper.updateById(article);

        // 解析 tagNames 并合并 tagIds，更新标签关联
        List<Long> resolvedTagIds = resolveTagIds(article);
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getArticleId, id));
        if (!resolvedTagIds.isEmpty()) {
            saveArticleTags(id, resolvedTagIds);
        }

        // 更新分类文章数量（兼容分类变更）
        if (existing.getCategoryId() != null) {
            categoryMapper.updateArticleCount(existing.getCategoryId());
        }
        if (article.getCategoryId() != null && !Objects.equals(article.getCategoryId(), existing.getCategoryId())) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }

        // 清理未使用的文件资源
        sysFileService.cleanUnusedFiles(id, article.getContent(), article.getCoverImage());

        // 将新引用的文件转为永久状态
        convertArticleFilesToPermanent(article);

        return getArticleById(id, false, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id, Long operatorId) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        User operator = accessService.getUserOrNull(operatorId);
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.isAdmin(operator)) {
            throw new ForbiddenException("仅管理员可删除文章");
        }

        // 逻辑删除
        articleMapper.deleteById(id);

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleLikeResult likeArticle(Long id, Long requesterId, String clientIp) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        if (!accessService.canViewArticle(requesterId, article)) {
            throw new ForbiddenException("当前文章无权互动");
        }

        if (requesterId != null) {
            LambdaQueryWrapper<UserArticleLike> w = new LambdaQueryWrapper<UserArticleLike>()
                    .eq(UserArticleLike::getUserId, requesterId)
                    .eq(UserArticleLike::getArticleId, id);
            UserArticleLike existing = userArticleLikeMapper.selectOne(w);
            if (existing == null) {
                UserArticleLike row = new UserArticleLike();
                row.setUserId(requesterId);
                row.setArticleId(id);
                userArticleLikeMapper.insert(row);
                articleMapper.incrementLikeCount(id);
            } else {
                userArticleLikeMapper.deleteById(existing.getId());
                articleMapper.decrementLikeCount(id);
            }
            Article fresh = articleMapper.selectById(id);
            int likes = fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
            boolean liked = userArticleLikeMapper.selectCount(new LambdaQueryWrapper<UserArticleLike>()
                    .eq(UserArticleLike::getUserId, requesterId)
                    .eq(UserArticleLike::getArticleId, id)) > 0;
            return new ArticleLikeResult(likes, liked);
        }

        assertAnonymousArticleLikeAllowed(id, clientIp);
        articleMapper.incrementLikeCount(id);
        Article fresh = articleMapper.selectById(id);
        int likes = fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
        return new ArticleLikeResult(likes, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long articleId, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        if (!accessService.canViewArticle(userId, article)) {
            throw new ForbiddenException("当前文章无权收藏");
        }
        LambdaQueryWrapper<UserArticleFavorite> w = new LambdaQueryWrapper<UserArticleFavorite>()
                .eq(UserArticleFavorite::getUserId, userId)
                .eq(UserArticleFavorite::getArticleId, articleId);
        UserArticleFavorite existing = userArticleFavoriteMapper.selectOne(w);
        if (existing == null) {
            UserArticleFavorite row = new UserArticleFavorite();
            row.setUserId(userId);
            row.setArticleId(articleId);
            userArticleFavoriteMapper.insert(row);
            return true;
        }
        userArticleFavoriteMapper.deleteById(existing.getId());
        return false;
    }

    @Override
    public Page<Article> getMyLikedArticlePage(Long userId, Integer page, Integer size) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return buildArticlePageFromUserRelation(userId, page, size, true);
    }

    @Override
    public Page<Article> getMyFavoriteArticlePage(Long userId, Integer page, Integer size) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return buildArticlePageFromUserRelation(userId, page, size, false);
    }

    /**
     * 按关联表时间倒序取文章，过滤当前仍可见，内存分页
     */
    private Page<Article> buildArticlePageFromUserRelation(Long userId, int page, int size, boolean likes) {
        List<Long> articleIdsOrdered;
        if (likes) {
            LambdaQueryWrapper<UserArticleLike> w = new LambdaQueryWrapper<UserArticleLike>()
                    .eq(UserArticleLike::getUserId, userId)
                    .orderByDesc(UserArticleLike::getCreateTime);
            articleIdsOrdered = userArticleLikeMapper.selectList(w).stream()
                    .map(UserArticleLike::getArticleId)
                    .collect(Collectors.toList());
        } else {
            LambdaQueryWrapper<UserArticleFavorite> w = new LambdaQueryWrapper<UserArticleFavorite>()
                    .eq(UserArticleFavorite::getUserId, userId)
                    .orderByDesc(UserArticleFavorite::getCreateTime);
            articleIdsOrdered = userArticleFavoriteMapper.selectList(w).stream()
                    .map(UserArticleFavorite::getArticleId)
                    .collect(Collectors.toList());
        }

        List<Article> visible = new ArrayList<>();
        for (Long aid : articleIdsOrdered) {
            Article a = articleMapper.selectById(aid);
            if (a != null && accessService.canViewArticle(userId, a)) {
                fillArticleRelations(a);
                accessService.fillArticlePermissions(a, userId);
                fillArticleInteractionFlags(a, userId);
                visible.add(a);
            }
        }

        long total = visible.size();
        int from = (page - 1) * size;
        List<Article> records;
        if (from >= visible.size()) {
            records = List.of();
        } else {
            int to = Math.min(from + size, visible.size());
            records = new ArrayList<>(visible.subList(from, to));
        }
        Page<Article> p = new Page<>(page, size, total);
        p.setRecords(records);
        return p;
    }

    @Override
    public List<Article> getHotArticles(Integer limit) {
        return articleMapper.selectHotArticles(limit);
    }

    @Override
    public List<Article> getRecommendArticles(Integer limit) {
        return articleMapper.selectRecommendArticles(limit);
    }

    @Override
    public Map<String, Object> getSiteStats() {
        return articleMapper.selectSiteStats();
    }

    @Override
    public List<ArchiveYearVO> listArchives() {
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        w.eq(Article::getStatus, Article.Status.PUBLISHED)
                .eq(Article::getVisibility, Article.Visibility.PUBLIC)
                .isNotNull(Article::getPublishTime)
                .orderByDesc(Article::getPublishTime);
        w.select(Article::getId, Article::getTitle, Article::getPublishTime);
        List<Article> rows = articleMapper.selectList(w);

        Map<Integer, Map<Integer, List<ArchiveArticleItem>>> byYearMonth = new LinkedHashMap<>();
        for (Article a : rows) {
            LocalDateTime pt = a.getPublishTime();
            int year = pt.getYear();
            int month = pt.getMonthValue();

            ArchiveArticleItem item = new ArchiveArticleItem();
            item.setId(a.getId());
            item.setTitle(a.getTitle());
            item.setPublishTime(pt);
            item.setTags(tagMapper.selectTagsByArticleId(a.getId()));

            byYearMonth
                    .computeIfAbsent(year, y -> new LinkedHashMap<>())
                    .computeIfAbsent(month, m -> new ArrayList<>())
                    .add(item);
        }

        List<Integer> years = new ArrayList<>(byYearMonth.keySet());
        years.sort(Collections.reverseOrder());

        List<ArchiveYearVO> result = new ArrayList<>();
        for (Integer year : years) {
            ArchiveYearVO yvo = new ArchiveYearVO();
            yvo.setYear(year);
            Map<Integer, List<ArchiveArticleItem>> monthMap = byYearMonth.get(year);

            List<Integer> months = new ArrayList<>(monthMap.keySet());
            months.sort(Collections.reverseOrder());

            List<ArchiveMonthVO> monthVos = new ArrayList<>();
            int yearCount = 0;
            for (Integer m : months) {
                List<ArchiveArticleItem> articles = monthMap.get(m);
                ArchiveMonthVO mvo = new ArchiveMonthVO();
                mvo.setMonth(m);
                mvo.setCount(articles.size());
                mvo.setArticles(articles);
                monthVos.add(mvo);
                yearCount += articles.size();
            }
            yvo.setMonths(monthVos);
            yvo.setCount(yearCount);
            result.add(yvo);
        }
        return result;
    }

    /**
     * 填充文章关联数据
     */
    private void fillArticleRelations(Article article) {
        // 填充作者信息
        if (article.getAuthorId() != null) {
            User author = userMapper.selectById(article.getAuthorId());
            if (author != null) {
                author.setPassword(null);
                if (author.getTrustLevel() == null) {
                    author.setTrustLevel(User.TrustLevel.NORMAL);
                }
                userAccessProfileSupport.applyDisplayAvatar(author);
                article.setAuthor(author);
            }
        }

        // 填充分类信息
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            article.setCategory(category);
        }

        // 填充标签信息
        List<Tag> tags = tagMapper.selectTagsByArticleId(article.getId());
        article.setTags(tags);
    }

    /**
     * 将 article.tagIds 与 article.tagNames（findOrCreate 后）合并为最终要保存的 tagId 列表
     */
    private List<Long> resolveTagIds(Article article) {
        List<Long> ids = new ArrayList<>();
        if (article.getTagIds() != null) {
            ids.addAll(article.getTagIds());
        }
        if (article.getTagNames() != null && !article.getTagNames().isEmpty()) {
            for (String name : article.getTagNames()) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                Tag tag = tagService.findOrCreateByName(name.trim());
                if (tag != null && tag.getId() != null && !ids.contains(tag.getId())) {
                    ids.add(tag.getId());
                }
            }
        }
        return ids;
    }

    /**
     * 保存文章标签关联
     */
    private void saveArticleTags(Long articleId, List<Long> tagIds) {
        for (Long tagId : tagIds) {
            ArticleTag at = new ArticleTag();
            at.setArticleId(articleId);
            at.setTagId(tagId);
            articleTagMapper.insert(at);
        }
    }

    /**
     * 生成摘要（从内容中提取前200字）
     */
    private String generateSummary(String content) {
        // 移除Markdown标记
        String text = content.replaceAll("[#*\\`\\[\\]!()\\-_>]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }

    /**
     * 将文章引用的所有文件转为永久状态
     */
    private void convertArticleFilesToPermanent(Article article) {
        if (article == null) {
            return;
        }

        // 收集所有引用的文件URL
        List<String> fileUrls = new ArrayList<>();

        // 提取内容中的图片URL
        if (StringUtils.hasText(article.getContent())) {
            fileUrls.addAll(sysFileService.extractImageUrlsFromContent(article.getContent()));
        }

        // 添加封面URL
        if (StringUtils.hasText(article.getCoverImage())) {
            fileUrls.add(article.getCoverImage());
        }

        // 去重并转为永久状态
        if (!fileUrls.isEmpty()) {
            List<String> uniqueUrls = fileUrls.stream()
                    .distinct()
                    .collect(Collectors.toList());

            // 内容图片
            sysFileService.convertToPermanent(uniqueUrls, com.chen404.domain.entity.SysFile.RefType.ARTICLE_CONTENT, article.getId());

            // 封面单独标记
            if (StringUtils.hasText(article.getCoverImage())) {
                sysFileService.convertToPermanent(
                        List.of(article.getCoverImage()),
                        com.chen404.domain.entity.SysFile.RefType.ARTICLE_COVER,
                        article.getId()
                );
            }
        }
    }

    private void assertAnonymousArticleLikeAllowed(Long articleId, String clientIp) {
        String key = RedisKeys.articleLikeThrottle(articleId, normalizeClientIp(clientIp));
        if (!redisUtil.setIfAbsent(key, "1", Duration.ofMillis(LIKE_COOLDOWN_MS))) {
            throw new TooManyRequestsException("您已点过赞了，无需重复点赞");
        }
    }

    private String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "anonymous";
        }
        return clientIp.trim();
    }
}
