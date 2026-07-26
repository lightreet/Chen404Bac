package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.chen404.service.CommentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommentServiceImplTest {

    @Test
    void shouldBuildFlatModerationRowsWithSourceContext() {
        initTableInfo(Comment.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        CommentService commentService = mock(CommentService.class);
        AdminCommentServiceImpl service = createService(commentMapper, articleMapper, commentService);

        Comment articleComment = comment(1L, 10L, Comment.Status.PENDING, "文章评论");
        Comment guestbookComment = comment(2L, null, Comment.Status.PENDING, "留言板留言");
        Page<Comment> commentPage = new Page<>(1, 20, 2);
        commentPage.setRecords(List.of(articleComment, guestbookComment));
        when(commentMapper.selectPage(
                any(Page.class),
                org.mockito.ArgumentMatchers.<Wrapper<Comment>>any()))
                .thenReturn(commentPage);

        Article article = new Article();
        article.setId(10L);
        article.setTitle("测试文章");
        when(articleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(article));

        PageResult<AdminCommentVO> result = service.getAdminComments(
                1,
                20,
                Comment.Status.PENDING,
                CommentSceneEnum.ALL,
                null
        );

        assertEquals(2L, result.getTotal());
        assertEquals("ARTICLE", result.getList().get(0).getScene());
        assertEquals("测试文章", result.getList().get(0).getArticleTitle());
        assertEquals("GUESTBOOK", result.getList().get(1).getScene());
    }

    @Test
    void shouldAggregateModerationStatistics() {
        initTableInfo(Comment.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        CommentService commentService = mock(CommentService.class);
        AdminCommentServiceImpl service = createService(commentMapper, articleMapper, commentService);
        when(commentMapper.selectCount(org.mockito.ArgumentMatchers.<Wrapper<Comment>>any()))
                .thenReturn(8L, 3L, 4L, 1L);

        AdminCommentStatsVO stats = service.getAdminCommentStats();

        assertEquals(8L, stats.getTotalCount());
        assertEquals(3L, stats.getPendingCount());
        assertEquals(4L, stats.getApprovedCount());
        assertEquals(1L, stats.getRejectedCount());
    }

    @Test
    void shouldDelegateReviewAndReturnAdminView() {
        initTableInfo(Comment.class);
        CommentMapper commentMapper = mock(CommentMapper.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        CommentService commentService = mock(CommentService.class);
        AdminCommentServiceImpl service = createService(commentMapper, articleMapper, commentService);

        Comment pending = comment(5L, null, Comment.Status.PENDING, "待审核留言");
        Comment approved = comment(5L, null, Comment.Status.APPROVED, "待审核留言");
        when(commentMapper.selectById(5L)).thenReturn(pending);
        when(commentService.reviewComment(5L, Comment.Status.APPROVED)).thenReturn(approved);

        AdminCommentVO result = service.reviewComment(5L, Comment.Status.APPROVED, 99L);

        assertEquals(Comment.Status.APPROVED, result.getStatus());
        assertEquals("GUESTBOOK", result.getScene());
        verify(commentService).reviewComment(5L, Comment.Status.APPROVED);
    }

    private AdminCommentServiceImpl createService(
            CommentMapper commentMapper,
            ArticleMapper articleMapper,
            CommentService commentService) {
        AdminCommentConverter converter = Mappers.getMapper(AdminCommentConverter.class);
        return new AdminCommentServiceImpl(commentMapper, articleMapper, commentService, converter);
    }

    private Comment comment(Long id, Long articleId, int status, String content) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setArticleId(articleId);
        comment.setParentId(0L);
        comment.setRootId(id);
        comment.setContent(content);
        comment.setAuthorName("测试用户");
        comment.setStatus(status);
        comment.setIsAdmin(0);
        comment.setLikeCount(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        return comment;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
