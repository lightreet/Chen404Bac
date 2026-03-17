package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.Category;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.mapper.TagMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.service.ArticleService;
import com.chen404.service.SysFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

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

    @Override
    public Page<Article> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, String keyword) {
        Page<Article> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        // 状态筛选
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        } else {
            // 默认查询已发布的文章
            wrapper.eq(Article::getStatus, 1);
        }

        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
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
        }
        return result;
    }

    @Override
    public Article getArticleById(Long id, boolean incrementView) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            return null;
        }

        // 增加浏览量
        if (incrementView) {
            articleMapper.incrementViewCount(id);
            article.setViewCount(article.getViewCount() + 1);
        }

        // 填充关联数据
        fillArticleRelations(article);

        return article;
    }

    @Override
    public Map<String, Article> getNeighbors(Long articleId) {
        Article current = articleMapper.selectById(articleId);
        if (current == null || current.getPublishTime() == null) {
            return Map.of();
        }
        Map<String, Article> result = new java.util.HashMap<>();

        // 上一篇：发布时间早于当前，取最近一篇
        LambdaQueryWrapper<Article> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.eq(Article::getStatus, 1)
                .lt(Article::getPublishTime, current.getPublishTime())
                .orderByDesc(Article::getPublishTime)
                .last("LIMIT 1");
        Article prev = articleMapper.selectOne(prevWrapper);
        if (prev != null) {
            result.put("prev", prev);
        }

        // 下一篇：发布时间晚于当前，取最早一篇
        LambdaQueryWrapper<Article> nextWrapper = new LambdaQueryWrapper<>();
        nextWrapper.eq(Article::getStatus, 1)
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
        // 设置默认值
        if (article.getViewCount() == null) {
            article.setViewCount(0);
        }
        if (article.getLikeCount() == null) {
            article.setLikeCount(0);
        }
        if (article.getCommentCount() == null) {
            article.setCommentCount(0);
        }
        if (article.getIsTop() == null) {
            article.setIsTop(0);
        }
        if (article.getIsRecommend() == null) {
            article.setIsRecommend(0);
        }
        if (article.getIsOriginal() == null) {
            article.setIsOriginal(1);
        }

        // 如果是发布状态，设置发布时间
        if (article.getStatus() == 1 && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 自动生成摘要（如果未填写）
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        articleMapper.insert(article);

        // 保存标签关联
        if (article.getTagIds() != null && !article.getTagIds().isEmpty()) {
            saveArticleTags(article.getId(), article.getTagIds());
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
    public Article updateArticle(Long id, Article article) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("文章不存在");
        }

        article.setId(id);

        // 如果从草稿变为发布，设置发布时间
        if (existing.getStatus() == 0 && article.getStatus() == 1) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 自动生成摘要
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        articleMapper.updateById(article);

        // 更新标签关联
        if (article.getTagIds() != null) {
            // 删除旧关联
            articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                    .eq(ArticleTag::getArticleId, id));
            // 保存新关联
            saveArticleTags(id, article.getTagIds());
        }

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }

        // 清理未使用的文件资源
        sysFileService.cleanUnusedFiles(id, article.getContent(), article.getCoverImage());

        // 将新引用的文件转为永久状态
        convertArticleFilesToPermanent(article);

        return getArticleById(id, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }

        // 逻辑删除
        articleMapper.deleteById(id);

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }
    }

    @Override
    public Integer likeArticle(Long id) {
        articleMapper.incrementLikeCount(id);
        Article article = articleMapper.selectById(id);
        return article != null ? article.getLikeCount() + 1 : 0;
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
}
