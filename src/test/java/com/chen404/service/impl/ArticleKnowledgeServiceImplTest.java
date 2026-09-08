package com.chen404.service.impl;

import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleAiChunk;
import com.chen404.mapper.ArticleAiChunkMapper;
import com.chen404.mapper.ArticleMapper;
import com.chen404.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证当前文章优先召回与普通召回共享对象权限边界。 */
class ArticleKnowledgeServiceImplTest {
    private ArticleMapper articleMapper;
    private ArticleAiChunkMapper chunkMapper;
    private AccessService accessService;
    private ArticleKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        chunkMapper = mock(ArticleAiChunkMapper.class);
        accessService = mock(AccessService.class);
        service = new ArticleKnowledgeServiceImpl(articleMapper, chunkMapper, accessService);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1,2", "1,3", "0,0", "2,0"})
    void deniedCurrentArticleNeverReturnsChunksEvenWithoutSearchKeywords(int status, int visibility) {
        Article article = article(status, visibility);
        when(articleMapper.selectById(42L)).thenReturn(article);
        when(accessService.canViewArticle(null, article)).thenReturn(false);

        assertTrue(service.searchVisibleChunks("", null, 42L, 3).isEmpty());
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void missingOrDeletedArticleNeverReadsStaleChunks() {
        assertTrue(service.searchVisibleChunks("", null, 42L, 3).isEmpty());
        Article deleted = article(1, 0);
        deleted.setDeleted(1);
        when(articleMapper.selectById(42L)).thenReturn(deleted);
        assertTrue(service.searchVisibleChunks("", 7L, 42L, 3).isEmpty());
        verifyNoInteractions(chunkMapper, accessService);
    }

    @Test
    void deniedCurrentArticleCannotReenterResultsThroughGlobalSearch() {
        Article article = article(0, 3);
        when(articleMapper.selectById(42L)).thenReturn(article);
        when(articleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(article));
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk()));
        when(accessService.canViewArticle(null, article)).thenReturn(false);

        assertTrue(service.searchVisibleChunks("secret", null, 42L, 3).isEmpty());
        verify(chunkMapper, times(1)).selectList(any());
    }

    @Test
    void permissionRevocationIsObservedOnTheNextSearch() {
        Article article = article(1, 2);
        when(articleMapper.selectById(42L)).thenReturn(article);
        when(accessService.canViewArticle(7L, article)).thenReturn(true, false);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk()));

        assertEquals(1, service.searchVisibleChunks("", 7L, 42L, 3).size());
        assertTrue(service.searchVisibleChunks("", 7L, 42L, 3).isEmpty());
        verify(chunkMapper, times(1)).selectList(any());
    }

    private Article article(int status, int visibility) {
        Article article = new Article();
        article.setId(42L);
        article.setStatus(status);
        article.setVisibility(visibility);
        article.setDeleted(0);
        return article;
    }

    private ArticleAiChunk chunk() {
        ArticleAiChunk chunk = new ArticleAiChunk();
        chunk.setArticleId(42L);
        chunk.setArticleTitle("secret title");
        chunk.setContentChunk("secret content");
        chunk.setChunkType("content");
        return chunk;
    }
}
