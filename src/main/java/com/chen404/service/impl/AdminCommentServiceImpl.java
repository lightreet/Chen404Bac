package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.converter.AdminCommentConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminCommentStatsVO;
import com.chen404.domain.dto.AdminCommentVO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Comment;
import com.chen404.domain.enums.CommentSceneEnum;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.CommentMapper;
import com.chen404.service.AdminCommentService;
import com.chen404.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端评论查询与审核服务实现。
 */
@Slf4j
@Service
public class AdminCommentServiceImpl implements AdminCommentService {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final CommentService commentService;
    private final AdminCommentConverter adminCommentConverter;

    public AdminCommentServiceImpl(
            CommentMapper commentMapper,
            ArticleMapper articleMapper,
            CommentService commentService,
            AdminCommentConverter adminCommentConverter) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.commentService = commentService;
        this.adminCommentConverter = adminCommentConverter;
    }

    @Override
    public PageResult<AdminCommentVO> getAdminComments(
            Integer page,
            Integer size,
            Integer status,
            CommentSceneEnum scene,
            String keyword) {
        validateQueryStatus(status);
        long current = page == null || page < 1 ? DEFAULT_PAGE : page;
        long pageSize = size == null || size < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        CommentSceneEnum normalizedScene = scene == null ? CommentSceneEnum.ALL : scene;

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Comment::getStatus, status);
        }
        applySceneFilter(wrapper, normalizedScene);
        applyKeywordFilter(wrapper, keyword);
        if (Objects.equals(status, Comment.Status.PENDING)) {
            wrapper.orderByAsc(Comment::getCreateTime).orderByAsc(Comment::getId);
        } else {
            wrapper.orderByDesc(Comment::getCreateTime).orderByDesc(Comment::getId);
        }

        Page<Comment> commentPage = commentMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<AdminCommentVO> list = toAdminCommentVOList(commentPage.getRecords());
        return new PageResult<>(list, commentPage.getTotal(), commentPage.getCurrent(), commentPage.getSize());
    }

    @Override
    public AdminCommentStatsVO getAdminCommentStats() {
        AdminCommentStatsVO stats = new AdminCommentStatsVO();
        stats.setTotalCount(commentMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.setPendingCount(countByStatus(Comment.Status.PENDING));
        stats.setApprovedCount(countByStatus(Comment.Status.APPROVED));
        stats.setRejectedCount(countByStatus(Comment.Status.REJECTED));
        return stats;
    }

    @Override
    public AdminCommentVO reviewComment(Long commentId, Integer status, Long adminId) {
        validateReviewStatus(status);
        Comment existing = commentMapper.selectById(commentId);
        if (existing == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        Integer previousStatus = existing.getStatus();
        Comment reviewed = commentService.reviewComment(commentId, status);
        if (!Objects.equals(previousStatus, status)) {
            log.info("[COMMENT_REVIEW] adminId={} commentId={} fromStatus={} toStatus={} scene={}",
                    adminId,
                    commentId,
                    previousStatus,
                    status,
                    existing.getArticleId() == null
                            ? CommentSceneEnum.GUESTBOOK
                            : CommentSceneEnum.ARTICLE);
        }
        return toAdminCommentVOList(List.of(reviewed)).get(0);
    }

    private void validateQueryStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != Comment.Status.PENDING
                && status != Comment.Status.APPROVED
                && status != Comment.Status.REJECTED) {
            throw new IllegalArgumentException("无效的评论状态");
        }
    }

    private void validateReviewStatus(Integer status) {
        if (status == null
                || (status != Comment.Status.APPROVED && status != Comment.Status.REJECTED)) {
            throw new IllegalArgumentException("无效的审核状态");
        }
    }

    private void applySceneFilter(
            LambdaQueryWrapper<Comment> wrapper,
            CommentSceneEnum scene) {
        if (scene == CommentSceneEnum.ARTICLE) {
            wrapper.isNotNull(Comment::getArticleId);
        } else if (scene == CommentSceneEnum.GUESTBOOK) {
            wrapper.isNull(Comment::getArticleId);
        }
    }

    private void applyKeywordFilter(
            LambdaQueryWrapper<Comment> wrapper,
            String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String normalizedKeyword = keyword.trim();
        wrapper.and(query -> query
                .like(Comment::getContent, normalizedKeyword)
                .or()
                .like(Comment::getAuthorName, normalizedKeyword)
                .or()
                .like(Comment::getAuthorEmail, normalizedKeyword));
    }

    private Long countByStatus(int status) {
        return commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, status));
    }

    private List<AdminCommentVO> toAdminCommentVOList(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Article> articleMap = loadArticleMap(comments);
        Map<Long, Comment> parentMap = loadParentMap(comments);
        return comments.stream()
                .map(comment -> toAdminCommentVO(comment, articleMap, parentMap))
                .toList();
    }

    private Map<Long, Article> loadArticleMap(List<Comment> comments) {
        Set<Long> articleIds = comments.stream()
                .map(Comment::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleMapper.selectBatchIds(articleIds).stream()
                .filter(article -> article != null && article.getId() != null)
                .collect(Collectors.toMap(Article::getId, Function.identity()));
    }

    private Map<Long, Comment> loadParentMap(List<Comment> comments) {
        Set<Long> parentIds = comments.stream()
                .map(Comment::getParentId)
                .filter(parentId -> parentId != null && parentId != 0L)
                .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return commentMapper.selectBatchIds(parentIds).stream()
                .filter(parent -> parent != null && parent.getId() != null)
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
    }

    private AdminCommentVO toAdminCommentVO(
            Comment comment,
            Map<Long, Article> articleMap,
            Map<Long, Comment> parentMap) {
        AdminCommentVO vo = adminCommentConverter.toVO(comment);
        if (comment.getArticleId() == null) {
            vo.setScene(CommentSceneEnum.GUESTBOOK.name());
        } else {
            vo.setScene(CommentSceneEnum.ARTICLE.name());
            Article article = articleMap.get(comment.getArticleId());
            vo.setArticleTitle(article == null ? null : article.getTitle());
        }
        Comment parent = parentMap.get(comment.getParentId());
        vo.setReplyToAuthorName(parent == null ? null : parent.getAuthorName());
        return vo;
    }
}
