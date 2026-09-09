package com.chen404.service.support;

import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.entity.Comment;
import com.chen404.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecentCommentViewAssemblerTest {
    private final ArticleService articles = mock(ArticleService.class);
    private final RecentCommentViewAssembler assembler = new RecentCommentViewAssembler(
            Mappers.getMapper(HomeViewConverter.class), articles);

    @Test
    void shouldResolvePublicTitlesOnceAndLeaveHiddenArticleTitleEmpty() {
        Comment visible = new Comment();
        visible.setArticleId(1L);
        Comment hidden = new Comment();
        hidden.setArticleId(2L);
        when(articles.getVisibleArticleTitles(Set.of(1L, 2L), null)).thenReturn(Map.of(1L, "公开标题"));

        var views = assembler.toList(List.of(visible, hidden, new Comment()));

        assertEquals("公开标题", views.get(0).getArticleTitle());
        assertNull(views.get(1).getArticleTitle());
        assertNull(views.get(2).getArticleTitle());
        verify(articles).getVisibleArticleTitles(Set.of(1L, 2L), null);
    }

    @Test
    void shouldSkipQueryForEmptyFeed() {
        assertTrue(assembler.toList(List.of()).isEmpty());
        verifyNoInteractions(articles);
    }
}
