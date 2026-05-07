package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.dto.CommentLikeResult;
import com.chen404.domain.dto.CreateCommentDTO;
import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Comment;
import com.chen404.domain.entity.CommentGuestToken;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserCommentLike;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.CommentMapper;
import com.chen404.mapper.CommentGuestTokenMapper;
import com.chen404.mapper.UserCommentLikeMapper;
import com.chen404.service.AccessService;
import com.chen404.service.CommentService;
import com.chen404.service.SiteConfigService;
import com.chen404.service.SysFileService;
import com.chen404.service.support.UserAccessProfileSupport;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Base64;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final long COMMENT_LIKE_COOLDOWN_MS = 60_000L;
    private static final Duration COMMENT_CREATE_USER_COOLDOWN = Duration.ofSeconds(10);
    private static final Duration COMMENT_CREATE_GUEST_COOLDOWN = Duration.ofSeconds(30);

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private AccessService accessService;

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Autowired
    private CommentGuestTokenMapper commentGuestTokenMapper;

    @Autowired
    private UserCommentLikeMapper userCommentLikeMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private SiteConfigService siteConfigService;

    @Override
    public Page<Comment> getCommentsByArticleId(Long articleId, int page, int size, Long requesterId) {
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

        fillLikedByMe(rootPage, requesterId);
        fillAuthorAvatarsFromSysFilePage(rootPage);
        return rootPage;
    }

    private void fillLikedByMe(Page<Comment> rootPage, Long requesterId) {
        if (requesterId == null || rootPage.getRecords().isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Comment root : rootPage.getRecords()) {
            ids.add(root.getId());
            List<Comment> ch = root.getChildren();
            if (ch != null) {
                for (Comment c : ch) {
                    ids.add(c.getId());
                }
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<UserCommentLike> rows = userCommentLikeMapper.selectList(new LambdaQueryWrapper<UserCommentLike>()
                .eq(UserCommentLike::getUserId, requesterId)
                .in(UserCommentLike::getCommentId, ids));
        Set<Long> likedIds = rows.stream().map(UserCommentLike::getCommentId).collect(Collectors.toSet());
        for (Comment root : rootPage.getRecords()) {
            root.setLikedByMe(likedIds.contains(root.getId()));
            List<Comment> ch = root.getChildren();
            if (ch != null) {
                for (Comment c : ch) {
                    c.setLikedByMe(likedIds.contains(c.getId()));
                }
            }
        }
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

        List<Comment> list = commentMapper.selectList(wrapper);
        fillAuthorAvatarsFromCommentTree(list);
        return list;
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
        SiteConfigDTO siteConfig = siteConfigService.getConfig();
        if (user == null && !Boolean.TRUE.equals(siteConfig.getCommentGuest())) {
            throw new ForbiddenException("当前站点暂不支持游客评论");
        }
        boolean isAdmin = user != null && accessService.isAdmin(user);
        assertCommentCreateAllowed(user, ip);

        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setContent(dto.getContent().trim());
        comment.setIp(ip);
        comment.setUserAgent(userAgent);

        if (user != null) {
            comment.setAuthorId(user.getId());
            comment.setAuthorName(user.getNickname());
            comment.setAuthorAvatar(user.getAvatar());
            Long avatarFid = user.getAvatarFileId();
            if (avatarFid == null && StringUtils.hasText(user.getAvatar())) {
                avatarFid = sysFileService.findAvatarFileIdForUser(user.getId(), user.getAvatar());
            }
            comment.setAuthorAvatarFileId(avatarFid);
            comment.setAuthorEmail(dto.getAuthorEmail());
        } else {
            if (!StringUtils.hasText(dto.getAuthorName())) {
                throw new IllegalArgumentException("游客评论必须提供昵称");
            }
            comment.setAuthorName(dto.getAuthorName().trim());
            comment.setAuthorEmail(dto.getAuthorEmail());
            comment.setAuthorWebsite(normalizeExternalUrl(dto.getAuthorWebsite()));
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
        comment.setStatus(isAdmin || !Boolean.TRUE.equals(siteConfig.getCommentAudit())
                ? Comment.Status.APPROVED
                : Comment.Status.PENDING);
        comment.setLikeCount(0);
        comment.setDeleted(0);

        commentMapper.insert(comment);

        if (parentId == 0L) {
            comment.setRootId(comment.getId());
            commentMapper.updateById(comment);
        }

        // 游客评论：生成自助删除 key（明文仅返回一次），仅保存 hash
        if (user == null) {
            String deleteKey = generateGuestDeleteKey();
            CommentGuestToken token = new CommentGuestToken();
            token.setCommentId(comment.getId());
            token.setTokenHash(sha256Hex(deleteKey));
            token.setExpireAt(java.time.LocalDateTime.now().plusDays(30));
            token.setCreateTime(java.time.LocalDateTime.now());
            commentGuestTokenMapper.insert(token);
            comment.setGuestDeleteKey(deleteKey);
        }

        if (comment.getArticleId() != null) {
            syncArticleCommentCount(comment.getArticleId());
        }

        fillSingleCommentAvatarFromSysFile(comment);
        return comment;
    }

    private void assertCommentCreateAllowed(User user, String ip) {
        String scope = user != null ? "user:" + user.getId() : "guest:" + normalizeClientIp(ip);
        Duration ttl = user != null ? COMMENT_CREATE_USER_COOLDOWN : COMMENT_CREATE_GUEST_COOLDOWN;
        String key = RedisKeys.commentCreateThrottle(scope);
        if (!redisUtil.setIfAbsent(key, "1", ttl)) {
            throw new TooManyRequestsException("发言太频繁了，请稍后再试");
        }
    }

    private String normalizeExternalUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (!value.matches("^https?://.+")) {
            throw new IllegalArgumentException("个人网站仅支持 http/https 链接");
        }
        return value;
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
        commentGuestTokenMapper.delete(new LambdaQueryWrapper<CommentGuestToken>()
                .in(CommentGuestToken::getCommentId, idsToDelete));

        if (comment.getArticleId() != null) {
            syncArticleCommentCount(comment.getArticleId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCommentAsGuest(Long id, String guestDeleteKey) {
        if (!StringUtils.hasText(guestDeleteKey)) {
            throw new ForbiddenException("缺少 guestDeleteKey");
        }
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        CommentGuestToken token = commentGuestTokenMapper.selectByCommentId(id);
        if (token == null) {
            throw new ForbiddenException("该评论不支持游客删除");
        }
        if (token.getExpireAt() != null && token.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ForbiddenException("guestDeleteKey 已过期");
        }
        String keyHash = sha256Hex(guestDeleteKey.trim());
        if (!Objects.equals(keyHash, token.getTokenHash())) {
            throw new ForbiddenException("guestDeleteKey 无效");
        }

        List<Long> idsToDelete = collectDescendantIds(id);
        idsToDelete.add(id);
        commentMapper.deleteBatchIds(idsToDelete);
        commentGuestTokenMapper.delete(new LambdaQueryWrapper<CommentGuestToken>()
                .in(CommentGuestToken::getCommentId, idsToDelete));

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

        fillSingleCommentAvatarFromSysFile(comment);
        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentLikeResult likeComment(Long id, Long userId, String clientIp) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        if (userId != null) {
            LambdaQueryWrapper<UserCommentLike> w = new LambdaQueryWrapper<UserCommentLike>()
                    .eq(UserCommentLike::getUserId, userId)
                    .eq(UserCommentLike::getCommentId, id);
            if (userCommentLikeMapper.selectCount(w) > 0) {
                int likes = comment.getLikeCount() == null ? 0 : comment.getLikeCount();
                return new CommentLikeResult(likes, true);
            }
            UserCommentLike row = new UserCommentLike();
            row.setUserId(userId);
            row.setCommentId(id);
            userCommentLikeMapper.insert(row);
            commentMapper.incrementLikeCount(id);
            Comment fresh = commentMapper.selectById(id);
            int likes = fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
            return new CommentLikeResult(likes, true);
        }
        assertAnonymousCommentLikeAllowed(id, clientIp);
        commentMapper.incrementLikeCount(id);
        Comment fresh = commentMapper.selectById(id);
        int likes = fresh.getLikeCount() == null ? 0 : fresh.getLikeCount();
        return new CommentLikeResult(likes, false);
    }

    private void assertAnonymousCommentLikeAllowed(Long commentId, String clientIp) {
        String ip = StringUtils.hasText(clientIp) ? clientIp.trim() : "anonymous";
        String key = RedisKeys.commentLikeThrottle(commentId, ip);
        if (!redisUtil.setIfAbsent(key, "1", Duration.ofMillis(COMMENT_LIKE_COOLDOWN_MS))) {
            throw new TooManyRequestsException("您已点过赞了，无需重复点赞");
        }
    }

    private static final SecureRandom GUEST_TOKEN_RANDOM = new SecureRandom();

    private String generateGuestDeleteKey() {
        byte[] bytes = new byte[24];
        GUEST_TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("hash 计算失败");
        }
    }

    private static String normalizeClientIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "anonymous";
        }
        return ip.trim().replace(':', '_');
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

    private void fillAuthorAvatarsFromSysFilePage(Page<Comment> page) {
        if (page == null || page.getRecords().isEmpty()) {
            return;
        }
        fillAuthorAvatarsFromCommentTree(page.getRecords());
    }

    private void fillAuthorAvatarsFromCommentTree(List<Comment> roots) {
        if (roots == null || roots.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Comment root : roots) {
            collectAuthorAvatarFileIds(root, ids);
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> idToUrl = loadAvatarUrlByFileIds(ids);
        for (Comment root : roots) {
            applyAuthorAvatarFromSysFile(root, idToUrl);
        }
    }

    private void collectAuthorAvatarFileIds(Comment c, List<Long> out) {
        if (c.getAuthorAvatarFileId() != null) {
            out.add(c.getAuthorAvatarFileId());
        }
        List<Comment> ch = c.getChildren();
        if (ch != null) {
            for (Comment x : ch) {
                collectAuthorAvatarFileIds(x, out);
            }
        }
    }

    private void applyAuthorAvatarFromSysFile(Comment c, Map<Long, String> idToUrl) {
        if (c.getAuthorAvatarFileId() != null) {
            String url = idToUrl.get(c.getAuthorAvatarFileId());
            if (StringUtils.hasText(url)) {
                c.setAuthorAvatar(url);
            }
        }
        List<Comment> ch = c.getChildren();
        if (ch != null) {
            for (Comment x : ch) {
                applyAuthorAvatarFromSysFile(x, idToUrl);
            }
        }
    }

    private Map<Long, String> loadAvatarUrlByFileIds(List<Long> rawIds) {
        List<Long> distinct = rawIds.stream().distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysFile> files = sysFileService.listByIds(distinct);
        Map<Long, String> map = new HashMap<>();
        for (SysFile f : files) {
            if (f.getId() != null && StringUtils.hasText(f.getFileUrl())) {
                map.put(f.getId(), f.getFileUrl());
            }
        }
        return map;
    }

    private void fillSingleCommentAvatarFromSysFile(Comment c) {
        if (c == null || c.getAuthorAvatarFileId() == null) {
            return;
        }
        SysFile f = sysFileService.getById(c.getAuthorAvatarFileId());
        if (f != null && StringUtils.hasText(f.getFileUrl())) {
            c.setAuthorAvatar(f.getFileUrl());
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
