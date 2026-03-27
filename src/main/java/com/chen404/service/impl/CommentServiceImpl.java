package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.dto.CreateCommentDTO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Comment;
import com.chen404.domain.entity.User;
import com.chen404.exception.ForbiddenException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.CommentMapper;
import com.chen404.service.AccessService;
import com.chen404.service.CommentService;
import com.chen404.service.support.UserAccessProfileSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private AccessService accessService;

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Override
    public Page<Comment> getCommentsByArticleId(Long articleId, int page, int size) {
        Page<Comment> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Comment> rootWrapper = new LambdaQueryWrapper<>();
        rootWrapper.eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, Comment.Status.APPROVED)
                .eq(Comment::getParentId, 0L)
                .orderByDesc(Comment::getCreateTime);

        Page<Comment> rootPage = commentMapper.selectPage(pageParam, rootWrapper);

        if (rootPage.getRecords().isEmpty()) {
            return rootPage;
        }

        List<Long> rootIds = rootPage.getRecords().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Comment> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.in(Comment::getRootId, rootIds)
                .ne(Comment::getParentId, 0L)
                .eq(Comment::getStatus, Comment.Status.APPROVED)
                .orderByAsc(Comment::getCreateTime);

        List<Comment> children = commentMapper.selectList(childWrapper);

        fillReplyToInfo(children);

        Map<Long, List<Comment>> childrenByRoot = children.stream()
                .collect(Collectors.groupingBy(Comment::getRootId));

        for (Comment root : rootPage.getRecords()) {
            root.setChildren(childrenByRoot.getOrDefault(root.getId(), Collections.emptyList()));
        }

        return rootPage;
    }

    @Override
    public Page<Comment> getGuestbookComments(int page, int size) {
        Page<Comment> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Comment> rootWrapper = new LambdaQueryWrapper<>();
        rootWrapper.isNull(Comment::getArticleId)
                .eq(Comment::getStatus, Comment.Status.APPROVED)
                .eq(Comment::getParentId, 0L)
                .orderByDesc(Comment::getCreateTime);

        Page<Comment> rootPage = commentMapper.selectPage(pageParam, rootWrapper);

        if (rootPage.getRecords().isEmpty()) {
            return rootPage;
        }

        List<Long> rootIds = rootPage.getRecords().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<Comment> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.in(Comment::getRootId, rootIds)
                .ne(Comment::getParentId, 0L)
                .eq(Comment::getStatus, Comment.Status.APPROVED)
                .orderByAsc(Comment::getCreateTime);

        List<Comment> children = commentMapper.selectList(childWrapper);
        fillReplyToInfo(children);

        Map<Long, List<Comment>> childrenByRoot = children.stream()
                .collect(Collectors.groupingBy(Comment::getRootId));

        for (Comment root : rootPage.getRecords()) {
            root.setChildren(childrenByRoot.getOrDefault(root.getId(), Collections.emptyList()));
        }

        return rootPage;
    }

    @Override
    public List<Comment> getRecentComments(int limit) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getStatus, Comment.Status.APPROVED)
                .orderByDesc(Comment::getCreateTime)
                .last("LIMIT " + Math.min(limit, 20));

        return commentMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Comment createComment(CreateCommentDTO dto, Long userId, String ip, String userAgent) {
        if (!StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("评论内容不能为空");
        }

        if (dto.getArticleId() != null) {
            Article article = articleMapper.selectById(dto.getArticleId());
            if (article == null) {
                throw new IllegalArgumentException("文章不存在");
            }
            if (!accessService.canCommentArticle(userId, article)) {
                throw new ForbiddenException("当前用户无权评论此文章");
            }
        }

        User user = userAccessProfileSupport.loadUserProfile(userId);
        boolean isAdmin = user != null && accessService.isAdmin(user);

        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setContent(dto.getContent());
        comment.setIp(ip);
        comment.setUserAgent(userAgent);

        if (user != null) {
            comment.setAuthorId(user.getId());
            comment.setAuthorName(user.getNickname());
            comment.setAuthorAvatar(user.getAvatar());
            comment.setAuthorEmail(dto.getAuthorEmail());
        } else {
            if (!StringUtils.hasText(dto.getAuthorName())) {
                throw new IllegalArgumentException("游客评论必须提供昵称");
            }
            comment.setAuthorName(dto.getAuthorName());
            comment.setAuthorEmail(dto.getAuthorEmail());
            comment.setAuthorWebsite(dto.getAuthorWebsite());
        }

        long parentId = dto.getParentId() != null ? dto.getParentId() : 0L;
        comment.setParentId(parentId);

        if (parentId != 0L) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父评论不存在");
            }
            // 回复评论必须与当前提交的 articleId 上下文一致（文章评论不能串到其他文章或留言板）
            if (!Objects.equals(parent.getArticleId(), dto.getArticleId())) {
                throw new IllegalArgumentException("父评论与当前评论上下文不一致");
            }
            if (!Objects.equals(parent.getStatus(), Comment.Status.APPROVED)) {
                throw new IllegalArgumentException("父评论不可回复");
            }
            comment.setRootId(parent.getRootId() != null && parent.getRootId() != 0L
                    ? parent.getRootId()
                    : parent.getId());
        } else {
            comment.setRootId(0L);
        }

        comment.setIsAdmin(isAdmin ? 1 : 0);
        // 当前阶段不走审核流，评论默认直接发布
        comment.setStatus(Comment.Status.APPROVED);
        comment.setLikeCount(0);
        comment.setDeleted(0);

        commentMapper.insert(comment);

        if (parentId == 0L) {
            comment.setRootId(comment.getId());
            commentMapper.updateById(comment);
        }

        if (comment.getArticleId() != null) {
            syncArticleCommentCount(comment.getArticleId());
        }

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id, Long userId) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }

        User user = userAccessProfileSupport.loadUserProfile(userId);
        boolean isAdmin = user != null && accessService.isAdmin(user);
        boolean isOwner = userId != null && userId.equals(comment.getAuthorId());

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("无权删除此评论");
        }

        List<Long> idsToDelete = collectDescendantIds(id);
        idsToDelete.add(id);
        commentMapper.deleteBatchIds(idsToDelete);

        if (comment.getArticleId() != null) {
            syncArticleCommentCount(comment.getArticleId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Comment reviewComment(Long id, int status) {
        if (status != Comment.Status.APPROVED && status != Comment.Status.REJECTED) {
            throw new IllegalArgumentException("无效的审核状态");
        }

        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }

        comment.setStatus(status);
        commentMapper.updateById(comment);

        if (comment.getArticleId() != null) {
            syncArticleCommentCount(comment.getArticleId());
        }

        return comment;
    }

    @Override
    public int likeComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        commentMapper.incrementLikeCount(id);
        return (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + 1;
    }

    private void syncArticleCommentCount(Long articleId) {
        int count = commentMapper.selectApprovedCountByArticleId(articleId);
        Article update = new Article();
        update.setId(articleId);
        update.setCommentCount(count);
        articleMapper.updateById(update);
    }

    private void fillReplyToInfo(List<Comment> children) {
        Set<Long> parentIds = children.stream()
                .map(Comment::getParentId)
                .filter(pid -> pid != null && pid != 0L)
                .collect(Collectors.toSet());

        if (parentIds.isEmpty()) return;

        List<Comment> parents = commentMapper.selectBatchIds(parentIds);
        Map<Long, Comment> parentMap = parents.stream()
                .collect(Collectors.toMap(Comment::getId, c -> c, (a, b) -> a));

        for (Comment child : children) {
            Comment parent = parentMap.get(child.getParentId());
            if (parent != null) {
                child.setReplyToAuthorName(parent.getAuthorName());
                child.setReplyToUserId(parent.getAuthorId());
            }
        }
    }

    /**
     * 级联收集子孙评论 ID（不包含入参本身）。
     */
    private List<Long> collectDescendantIds(Long rootId) {
        List<Long> descendants = new ArrayList<>();
        List<Long> currentLevel = Collections.singletonList(rootId);

        while (!currentLevel.isEmpty()) {
            LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Comment::getParentId, currentLevel).select(Comment::getId);
            List<Comment> children = commentMapper.selectList(wrapper);
            if (children.isEmpty()) {
                break;
            }
            currentLevel = children.stream().map(Comment::getId).collect(Collectors.toList());
            descendants.addAll(currentLevel);
        }

        return descendants;
    }
}
