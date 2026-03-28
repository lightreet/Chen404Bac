package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.mapper.TagMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.service.AccessService;
import com.chen404.service.ArticleService;
import com.chen404.service.SysFileService;
import com.chen404.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private static final long LIKE_COOLDOWN_MS = 60_000L;
    private static final int LIKE_THROTTLE_CLEANUP_INTERVAL = 256;
    private static final ConcurrentHashMap<String, Long> LIKE_THROTTLE_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger LIKE_THROTTLE_OPS = new AtomicInteger();

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

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Article::getTitle, keyword)
                    .or()
                    .like(Article::getSummary, keyword));
        }

        // 排序：置顶优先，然后按发布时间倒序
        wrapper.orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishTime);

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
        wrapper.orderByDesc(Article::getUpdateTime).orderByDesc(Article::getCreateTime);
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

        return article;
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
    public Integer likeArticle(Long id, Long requesterId, String clientIp) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        if (!accessService.canViewArticle(requesterId, article)) {
            throw new ForbiddenException("当前文章无权互动");
        }
        assertLikeAllowed(id, requesterId, clientIp);
        articleMapper.incrementLikeCount(id);
        int currentLikes = article.getLikeCount() == null ? 0 : article.getLikeCount();
        return currentLikes + 1;
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

    private void assertLikeAllowed(Long articleId, Long requesterId, String clientIp) {
        long now = System.currentTimeMillis();
        cleanupLikeThrottleCacheIfNeeded(now);

        String actorKey = buildLikeActorKey(articleId, requesterId, clientIp);
        Long lastLikeAt = LIKE_THROTTLE_CACHE.putIfAbsent(actorKey, now);
        if (lastLikeAt == null) {
            return;
        }

        if (now - lastLikeAt < LIKE_COOLDOWN_MS) {
            throw new TooManyRequestsException("点赞过于频繁，请稍后再试");
        }

        LIKE_THROTTLE_CACHE.put(actorKey, now);
    }

    private void cleanupLikeThrottleCacheIfNeeded(long now) {
        if (LIKE_THROTTLE_OPS.incrementAndGet() % LIKE_THROTTLE_CLEANUP_INTERVAL != 0) {
            return;
        }
        LIKE_THROTTLE_CACHE.entrySet().removeIf(entry -> now - entry.getValue() >= LIKE_COOLDOWN_MS);
    }

    private String buildLikeActorKey(Long articleId, Long requesterId, String clientIp) {
        if (requesterId != null) {
            return "article:" + articleId + ":user:" + requesterId;
        }
        String normalizedIp = normalizeClientIp(clientIp);
        return "article:" + articleId + ":ip:" + normalizedIp;
    }

    private String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "anonymous";
        }
        return clientIp.trim();
    }
}
