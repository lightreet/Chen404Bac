package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.converter.ArticleCommandConverter;
import com.chen404.domain.PageBounds;
import com.chen404.domain.PageResult;
import com.chen404.domain.access.ArticleReadScope;
import com.chen404.domain.dto.ArchiveArticleItem;
import com.chen404.domain.dto.ArchiveMonthVO;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleLikeResult;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborsVO;
import com.chen404.domain.dto.ArticleSearchCriteria;
import com.chen404.domain.dto.ArticleTagVO;
import com.chen404.domain.dto.ArticleWriteCommand;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.UpdateArticleCommand;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.Tag;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserArticleFavorite;
import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.enums.AdminNotificationResourceTypeEnum;
import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.domain.event.AdminContentEvent;
import com.chen404.exception.ConflictException;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.mapper.CategoryMapper;
import com.chen404.mapper.UserArticleFavoriteMapper;
import com.chen404.mapper.UserArticleLikeMapper;
import com.chen404.service.AccessService;
import com.chen404.service.AdminContentEventPublisher;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.ArticleService;
import com.chen404.service.FileClaim;
import com.chen404.service.FileReferenceService;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.TagService;
import com.chen404.service.support.ArticlePolicyValidator;
import com.chen404.service.support.ArticleViewAssembler;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 文章用例编排；写入仅接收命令，读取返回专用视图，数据库实体留在服务内部。 */
@Service
public class ArticleServiceImpl implements ArticleService {

    private static final long LIKE_COOLDOWN_MS = 60_000L;
    private static final int DEFAULT_VISIBLE_SCAN_LIMIT = 60;
    private static final int NEIGHBOR_SCAN_LIMIT = 20;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ArticleTagMapper articleTagMapper;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private ProtectedFileAccessService protectedFileAccessService;

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
    private ArticleKnowledgeService articleKnowledgeService;

    @Autowired
    private FileReferenceService fileReferenceService;

    @Autowired
    private AdminContentEventPublisher adminContentEventPublisher;

    @Autowired
    private ArticleCommandConverter commandConverter;

    @Autowired
    private ArticleViewAssembler viewAssembler;

    @Override
    public PageResult<ArticleListItemVO> getArticlePage(Integer page, Integer size, Integer status, Long categoryId, Long tagId, Long authorId, String keyword, Long requesterId) {
        // 公共文章流固定为已发布；访问范围在数据库中应用，避免先扫描再分页。
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                ArticleStatusEnum.PUBLISHED.getValue(), categoryId, tagId, authorId, keyword);
        PageBounds bounds = PageBounds.of(page, size, PageBounds.DEFAULT_SIZE);
        Page<Article> result = articleMapper.selectReadablePage(new Page<>(bounds.current(), bounds.size()),
                criteria, ArticleReadScope.forUser(accessService.getUserOrNull(requesterId)), false);
        return viewAssembler.toPage(result, requesterId, false);
    }

    @Override
    public PageResult<ArticleListItemVO> getMyArticlePage(Long userId, Integer page, Integer size, Integer status, String keyword) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        PageBounds bounds = PageBounds.of(page, size, PageBounds.DEFAULT_SIZE);
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(status, null, null, userId, keyword);
        Page<Article> result = articleMapper.selectReadablePage(new Page<>(bounds.current(), bounds.size()),
                criteria, ArticleReadScope.forUser(accessService.getUserOrNull(userId)), true);
        return viewAssembler.toPage(result, userId, false);
    }

    @Override
    public ArticleDetailVO getArticleById(Long id, boolean incrementView, Long requesterId) {
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

        return viewAssembler.toDetail(article, requesterId);
    }

    @Override
    public ArticleNeighborsVO getNeighbors(Long articleId, Long requesterId) {
        Article current = articleMapper.selectById(articleId);
        if (current == null || current.getPublishTime() == null || !accessService.canViewArticle(requesterId, current)) {
            return new ArticleNeighborsVO();
        }
        ArticleReadScope scope = ArticleReadScope.forUser(accessService.getUserOrNull(requesterId));

        // 上一篇：发布时间早于当前，取最近一篇
        LambdaQueryWrapper<Article> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getValue())
                .lt(Article::getPublishTime, current.getPublishTime())
                .orderByDesc(Article::getPublishTime)
                .last("LIMIT " + NEIGHBOR_SCAN_LIMIT);
        Article prev = articleMapper.selectList(prevWrapper).stream()
                .filter(article -> scope.canRead(article))
                .findFirst()
                .orElse(null);

        // 下一篇：发布时间晚于当前，取最早一篇
        LambdaQueryWrapper<Article> nextWrapper = new LambdaQueryWrapper<>();
        nextWrapper.eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getValue())
                .gt(Article::getPublishTime, current.getPublishTime())
                .orderByAsc(Article::getPublishTime)
                .last("LIMIT " + NEIGHBOR_SCAN_LIMIT);
        Article next = articleMapper.selectList(nextWrapper).stream()
                .filter(article -> scope.canRead(article))
                .findFirst()
                .orElse(null);

        return viewAssembler.toNeighbors(prev, next);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleDetailVO createArticle(CreateArticleCommand command, Long operatorId) {
        ArticlePolicyValidator.validate(command);
        Article article = commandConverter.toEntity(command);
        article.setAuthorId(operatorId);
        User operator = accessService.getUserOrNull(article.getAuthorId());
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.canCreateArticle(article.getAuthorId())) {
            throw new ForbiddenException("仅知友或管理员可创建文章");
        }

        // 设置默认值
        article.setVersion(0);
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
            article.setVisibility(ArticleVisibilityEnum.PUBLIC.getValue());
        }
        if (article.getCommentPolicy() == null) {
            article.setCommentPolicy(ArticleCommentPolicyEnum.REGISTERED.getValue());
        }
        if (!accessService.canCurateArticle(article.getAuthorId())) {
            article.setIsTop(0);
            article.setIsRecommend(0);
        }
        normalizeArticleFileUrls(article);

        // 如果是发布状态，设置发布时间
        if (ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED) && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }

        // 自动生成摘要（如果未填写）
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        articleMapper.insert(article);

        // 解析 tagNames 为 ID 并合并到 tagIds，再保存标签关联
        List<Long> resolvedTagIds = resolveTagIds(command);
        if (!resolvedTagIds.isEmpty()) {
            saveArticleTags(article.getId(), resolvedTagIds);
        }

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }

        // 将文章中引用的文件转为永久状态
        claimArticleFiles(article, article.getAuthorId());

        persistCoverFileId(article.getId(), article.getCoverImage());
        fileReferenceService.syncArticleReferences(article.getId(), article.getContent(), article.getCoverImage());
        articleKnowledgeService.syncArticleChunks(article.getId());

        publishArticleCreatedEvent(article);
        return getArticleById(article.getId(), false, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleDetailVO updateArticle(Long id, UpdateArticleCommand command, Long operatorId) {
        ArticlePolicyValidator.validate(command);
        Article article = commandConverter.toEntity(command);
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        User operator = accessService.getUserOrNull(operatorId);
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.canManageArticle(operatorId, existing)) {
            throw new ForbiddenException("只能修改自己创建的文章");
        }

        article.setId(id);
        article.setAuthorId(existing.getAuthorId());
        Integer expectedVersion = article.getVersion() == null ? existing.getVersion() : article.getVersion();

        if (article.getVisibility() == null) {
            article.setVisibility(ArticleVisibilityEnum.fromValue(existing.getVisibility()).getValue());
        }
        if (article.getCommentPolicy() == null) {
            article.setCommentPolicy(ArticleCommentPolicyEnum.fromValue(existing.getCommentPolicy()).getValue());
        }
        if (!accessService.canCurateArticle(operatorId)) {
            article.setIsTop(existing.getIsTop());
            article.setIsRecommend(existing.getIsRecommend());
        }
        normalizeArticleFileUrls(article);

        // 如果从草稿变为发布，设置发布时间
        if (ArticleStatusEnum.is(existing.getStatus(), ArticleStatusEnum.DRAFT)
                && ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED)) {
            article.setPublishTime(existing.getPublishTime() == null
                    ? LocalDateTime.now()
                    : existing.getPublishTime());
        }

        // 自动生成摘要
        if (!StringUtils.hasText(article.getSummary()) && StringUtils.hasText(article.getContent())) {
            String summary = generateSummary(article.getContent());
            article.setSummary(summary);
        }

        if (articleMapper.updateEditableFields(article, expectedVersion, accessService.canCurateArticle(operatorId)) != 1) {
            throw new ConflictException("文章已被其他操作修改，请刷新后重试");
        }

        // 解析 tagNames 并合并 tagIds，更新标签关联
        List<Long> resolvedTagIds = resolveTagIds(command);
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
        claimArticleFiles(article, operatorId);

        persistCoverFileId(id, article.getCoverImage());
        fileReferenceService.syncArticleReferences(id, article.getContent(), article.getCoverImage());
        articleKnowledgeService.syncArticleChunks(id);

        if (!ArticleStatusEnum.is(existing.getStatus(), ArticleStatusEnum.PUBLISHED)
                && ArticleStatusEnum.is(article.getStatus(), ArticleStatusEnum.PUBLISHED)) {
            publishArticlePublishedEvent(article);
        }
        return getArticleById(id, false, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id, Long operatorId) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        User operator = accessService.getUserOrNull(operatorId);
        if (operator == null) {
            throw new UnauthorizedException();
        }
        if (!accessService.canManageArticle(operatorId, article)) {
            throw new ForbiddenException("只能删除自己创建的文章");
        }

        fileReferenceService.removeByOwner(
                com.chen404.domain.entity.FileReference.ModuleCode.ARTICLE,
                com.chen404.domain.entity.FileReference.BizType.ARTICLE_CONTENT,
                id
        );
        fileReferenceService.removeByOwner(
                com.chen404.domain.entity.FileReference.ModuleCode.ARTICLE,
                com.chen404.domain.entity.FileReference.BizType.ARTICLE_COVER,
                id
        );

        // 逻辑删除
        articleMapper.deleteById(id);

        // 更新分类文章数量
        if (article.getCategoryId() != null) {
            categoryMapper.updateArticleCount(article.getCategoryId());
        }
        articleKnowledgeService.removeArticleChunks(id);
    }

    private void publishArticleCreatedEvent(Article article) {
        AdminNotificationEventTypeEnum eventType = ArticleStatusEnum.is(
                article.getStatus(),
                ArticleStatusEnum.PUBLISHED
        )
                ? AdminNotificationEventTypeEnum.ARTICLE_PUBLISHED
                : AdminNotificationEventTypeEnum.ARTICLE_CREATED;
        adminContentEventPublisher.publish(new AdminContentEvent(
                eventType,
                article.getAuthorId(),
                AdminNotificationResourceTypeEnum.ARTICLE,
                article.getId(),
                article.getTitle()
        ));
    }

    private void publishArticlePublishedEvent(Article article) {
        adminContentEventPublisher.publish(new AdminContentEvent(
                AdminNotificationEventTypeEnum.ARTICLE_PUBLISHED,
                article.getAuthorId(),
                AdminNotificationResourceTypeEnum.ARTICLE,
                article.getId(),
                article.getTitle()
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleLikeResult likeArticle(Long id, Long requesterId, String clientIp) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new ResourceNotFoundException("文章不存在");
        }
        if (!accessService.canViewArticle(requesterId, article)) {
            throw new ForbiddenException("当前文章无权互动");
        }

        if (requesterId != null) {
            int deleted = userArticleLikeMapper.deleteLike(requesterId, id);
            boolean liked;
            if (deleted > 0) {
                articleMapper.decrementLikeCount(id);
                liked = false;
            } else {
                int inserted = userArticleLikeMapper.insertLikeIfAbsent(requesterId, id);
                if (inserted > 0) {
                    articleMapper.incrementLikeCount(id);
                }
                liked = true;
            }
            Article fresh = articleMapper.selectById(id);
            int likes = fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
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
            throw new ResourceNotFoundException("文章不存在");
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
        } else {
            userArticleFavoriteMapper.deleteById(existing.getId());
            return false;
        }
    }

    @Override
    public PageResult<ArticleListItemVO> getMyLikedArticlePage(Long userId, Integer page, Integer size) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return buildArticlePageFromUserRelation(userId, page, size, true);
    }

    @Override
    public PageResult<ArticleListItemVO> getMyFavoriteArticlePage(Long userId, Integer page, Integer size) {
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return buildArticlePageFromUserRelation(userId, page, size, false);
    }

    /**
     * 按关联表时间倒序，对当前仍可见的文章进行数据库分页
     */
    private PageResult<ArticleListItemVO> buildArticlePageFromUserRelation(Long userId, Integer page, Integer size, boolean likes) {
        PageBounds bounds = PageBounds.of(page, size, PageBounds.DEFAULT_SIZE);
        Page<Article> result = articleMapper.selectRelatedPage(new Page<>(bounds.current(), bounds.size()), userId,
                likes, ArticleReadScope.forUser(accessService.getUserOrNull(userId)));
        return viewAssembler.toPage(result, userId, true);
    }

    @Override
    public List<ArticleListItemVO> getHotArticles(Integer limit, Long requesterId) {
        int safeLimit = limit == null || limit < 1 ? 10 : limit;
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getValue())
                .orderByDesc(Article::getViewCount)
                .last("LIMIT " + DEFAULT_VISIBLE_SCAN_LIMIT);
        return viewAssembler.toList(filterVisibleArticles(articleMapper.selectList(wrapper), requesterId, safeLimit), requesterId, false);
    }

    @Override
    public List<ArticleListItemVO> getRecommendArticles(Integer limit, Long requesterId) {
        int safeLimit = limit == null || limit < 1 ? 6 : limit;
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getValue())
                .eq(Article::getIsRecommend, 1)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + DEFAULT_VISIBLE_SCAN_LIMIT);
        return viewAssembler.toList(filterVisibleArticles(articleMapper.selectList(wrapper), requesterId, safeLimit), requesterId, false);
    }

    @Override
    public Map<String, Object> getSiteStats() {
        return articleMapper.selectSiteStats();
    }

    @Override
    public List<ArchiveYearVO> listArchives(Long requesterId) {
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        w.eq(Article::getStatus, ArticleStatusEnum.PUBLISHED.getValue())
                .isNotNull(Article::getPublishTime)
                .orderByDesc(Article::getPublishTime);
        w.select(
                Article::getId,
                Article::getTitle,
                Article::getAuthorId,
                Article::getStatus,
                Article::getVisibility,
                Article::getPublishTime);
        List<Article> rows = filterVisibleArticles(articleMapper.selectList(w), requesterId, null);
        Map<Long, List<ArticleTagVO>> tagsByArticleId = viewAssembler.tagsByArticleIds(
                rows.stream().map(Article::getId).toList());

        Map<Integer, Map<Integer, List<ArchiveArticleItem>>> byYearMonth = new LinkedHashMap<>();
        for (Article a : rows) {
            LocalDateTime pt = a.getPublishTime();
            int year = pt.getYear();
            int month = pt.getMonthValue();

            ArchiveArticleItem item = new ArchiveArticleItem();
            item.setId(a.getId());
            item.setTitle(a.getTitle());
            item.setPublishTime(pt);
            item.setTags(tagsByArticleId.getOrDefault(a.getId(), List.of()));

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

    /** 保存/更新文章后，将封面 URL 解析为 sys_file.id 写入 article.cover_file_id */
    private void persistCoverFileId(Long articleId, String coverImage) {
        if (articleId == null) {
            return;
        }
        Long fid = sysFileService.findCoverFileIdForArticle(articleId, coverImage);
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .set(Article::getCoverFileId, fid));
    }

    /**
     * 将命令中的 tagIds 与 tagNames（findOrCreate 后）合并为最终要保存的 tagId 列表
     */
    private List<Long> resolveTagIds(ArticleWriteCommand command) {
        List<Long> ids = new ArrayList<>();
        if (command.getTagIds() != null) {
            ids.addAll(command.getTagIds());
        }
        if (command.getTagNames() != null && !command.getTagNames().isEmpty()) {
            for (String name : command.getTagNames()) {
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
    private void claimArticleFiles(Article article, Long operatorId) {
        if (article == null) {
            return;
        }

        List<String> contentUrls = new ArrayList<>();
        if (StringUtils.hasText(article.getContent())) {
            contentUrls.addAll(sysFileService.extractImageUrlsFromContent(article.getContent()));
        }
        if (!contentUrls.isEmpty()) {
            sysFileService.claimPermanentFiles(
                    operatorId,
                    contentUrls.stream().distinct().map(FileClaim::byUrl).toList(),
                    SysFile.RefType.ARTICLE_CONTENT,
                    article.getId()
            );
        }
        if (StringUtils.hasText(article.getCoverImage())) {
            sysFileService.claimPermanentFiles(
                    operatorId,
                    List.of(FileClaim.byUrl(article.getCoverImage())),
                    SysFile.RefType.ARTICLE_COVER,
                    article.getId()
            );
        }
    }

    private void normalizeArticleFileUrls(Article article) {
        if (article == null) {
            return;
        }
        article.setContent(protectedFileAccessService.normalizeContent(article.getContent()));
        article.setCoverImage(protectedFileAccessService.normalizeUrl(article.getCoverImage()));
    }

    private void assertAnonymousArticleLikeAllowed(Long articleId, String clientIp) {
        String key = RedisKeys.articleLikeThrottle(articleId, normalizeClientIp(clientIp));
        if (!redisUtil.setIfAbsent(key, "1", Duration.ofMillis(LIKE_COOLDOWN_MS))) {
            throw new TooManyRequestsException("您已点过赞了，无需重复点赞");
        }
    }

    private List<Article> filterVisibleArticles(List<Article> candidates, Long requesterId, Integer limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        ArticleReadScope scope = ArticleReadScope.forUser(accessService.getUserOrNull(requesterId));
        List<Article> result = new ArrayList<>();
        for (Article article : candidates) {
            if (!scope.canRead(article)) {
                continue;
            }
            result.add(article);
            if (limit != null && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Override
    public Map<Long, String> getVisibleArticleTitles(Collection<Long> articleIds, Long requesterId) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        ArticleReadScope scope = ArticleReadScope.forUser(accessService.getUserOrNull(requesterId));
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .in(Article::getId, articleIds)
                        .select(Article::getId, Article::getTitle, Article::getAuthorId, Article::getStatus, Article::getVisibility))
                .stream().filter(scope::canRead).filter(article -> StringUtils.hasText(article.getTitle()))
                .collect(Collectors.toMap(Article::getId, Article::getTitle));
    }

    private String normalizeClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "anonymous";
        }
        return clientIp.trim();
    }
}
