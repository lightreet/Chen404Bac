package com.chen404.service.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.converter.ArticleViewConverter;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleViewAssemblerTest {
    private final UserMapper users = mock(UserMapper.class);
    private final CategoryMapper categories = mock(CategoryMapper.class);
    private final TagMapper tags = mock(TagMapper.class);
    private final ArticleTagMapper relations = mock(ArticleTagMapper.class);
    private final UserArticleLikeMapper likes = mock(UserArticleLikeMapper.class);
    private final UserArticleFavoriteMapper favorites = mock(UserArticleFavoriteMapper.class);
    private final SysFileService files = mock(SysFileService.class);
    private final ProtectedFileAccessService fileAccess = mock(ProtectedFileAccessService.class);
    private final UserAccessProfileSupport profiles = mock(UserAccessProfileSupport.class);
    private final AccessService access = mock(AccessService.class);
    private final ArticleViewAssembler assembler = new ArticleViewAssembler(Mappers.getMapper(ArticleViewConverter.class),
            users, categories, tags, relations, likes, favorites, files, fileAccess, profiles, access);

    @BeforeEach
    void initializeLambdaMetadata() {
        for (Class<?> type : List.of(ArticleTag.class, UserArticleLike.class, UserArticleFavorite.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @Test
    void assemblesRelationsAndPermissionsWithoutMutatingArticleOrExposingSecrets() throws Exception {
        Article article = new Article();
        article.setId(1L);
        article.setAuthorId(7L);
        article.setCategoryId(8L);
        article.setCoverFileId(9L);
        article.setCoverImage("stored-cover");
        article.setContent("stored-content");
        article.setPassword("article-secret");
        article.setVersion(3);
        User author = new User();
        author.setId(7L);
        author.setUsername("author");
        author.setPassword("account-secret");
        Category category = new Category();
        category.setId(8L);
        category.setName("backend");
        SysFile cover = new SysFile();
        cover.setId(9L);
        cover.setFileUrl("current-cover");
        Tag tag = new Tag();
        tag.setId(10L);
        tag.setName("java");
        tag.setStatus(1);
        ArticleTag relation = new ArticleTag();
        relation.setArticleId(1L);
        relation.setTagId(10L);
        when(users.selectBatchIds(anyCollection())).thenReturn(List.of(author));
        when(categories.selectBatchIds(anyCollection())).thenReturn(List.of(category));
        when(files.findByIds(anyCollection())).thenReturn(List.of(cover));
        when(relations.selectList(any())).thenReturn(List.of(relation));
        when(tags.selectBatchIds(anyCollection())).thenReturn(List.of(tag));
        when(access.canManageArticle(7L, article)).thenReturn(true);
        when(access.canCommentArticle(7L, article)).thenReturn(true);
        when(fileAccess.issueContentUrls(anyString(), anyString(), eq(1L))).thenReturn("signed-content");
        when(fileAccess.issueUrlForReference(eq("current-cover"), anyString(), eq(1L))).thenReturn("signed-cover");

        var ownerView = assembler.toDetail(article, 7L);
        var guestView = assembler.toDetail(article, null);

        assertEquals("author", ownerView.getAuthor().getUsername());
        assertEquals("backend", ownerView.getCategory().getName());
        assertEquals("java", ownerView.getTags().get(0).getName());
        assertTrue(ownerView.getCanEdit());
        assertTrue(ownerView.getCanComment());
        assertFalse(guestView.getCanEdit());
        assertEquals("signed-cover", ownerView.getCoverImage());
        assertEquals("signed-content", ownerView.getContent());
        assertEquals(3, ownerView.getVersion());
        assertEquals("stored-cover", article.getCoverImage());
        assertEquals("stored-content", article.getContent());
        String json = new ObjectMapper().writeValueAsString(ownerView);
        assertFalse(json.contains("article-secret"));
        assertFalse(json.contains("account-secret"));
    }

    @Test
    void emptyPageDoesNotLoadAnyRelations() {
        var result = assembler.toPage(new Page<>(2, 10, 0), null, true);
        assertTrue(result.getList().isEmpty());
        assertEquals(2L, result.getPage());
        verifyNoInteractions(users, categories, tags, relations, likes, favorites, files, fileAccess, profiles, access);
    }

    @Test
    void incompleteRelationsAreRepresentedAsEmptyViews() {
        Article article = new Article();
        article.setId(1L);
        var result = assembler.toList(List.of(article), null, false).get(0);
        assertNull(result.getAuthor());
        assertNull(result.getCategory());
        assertTrue(result.getTags().isEmpty());
        verifyNoInteractions(users, categories, files);
    }
}
