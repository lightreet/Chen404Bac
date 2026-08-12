package com.chen404.service.support.scenario.recommend;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.entity.Article;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.service.AccessService;
import com.chen404.service.FeatureToggleService;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleRecommendScenarioDefinitionTest {

    @Test
    void shouldReturnBeforeQueryingArticlesWhenFeatureIsDisabled() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
        AccessService accessService = mock(AccessService.class);
        ArticleRecommendScenarioDefinition definition = new ArticleRecommendScenarioDefinition(
                articleMapper,
                articleTagMapper,
                accessService,
                new AiRuntimeProperties(),
                mock(FeatureToggleService.class)
        );

        ArticleRecommendScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.ARTICLE_RECOMMEND,
                new ArticleRecommendScenarioRequest(100L, "article", 7L, "推荐一下", 3)
        )).data();

        assertEquals(List.of(), result.items());
        assertTrue(result.reason().contains("未开启"));
        verifyNoInteractions(articleMapper, articleTagMapper, accessService);
    }

    @Test
    void shouldRankSameCategoryAndSharedTagArticlesAheadOfFallbackCandidates() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
        AccessService accessService = mock(AccessService.class);
        FeatureToggleService featureToggleService = enabledFeatures();

        Article current = article(100L, "当前文章", 9L, 0, 1, 0);
        Article best = article(200L, "同分类同标签", 9L, 0, 0, 1);
        Article backup = article(201L, "仅站点推荐", 8L, 0, 0, 1);
        Article hidden = article(202L, "不可见文章", 9L, 0, 0, 1);

        when(articleMapper.selectById(100L)).thenReturn(current);
        when(articleMapper.selectList(any())).thenReturn(List.of(current, hidden, backup, best));
        when(articleTagMapper.selectTagIdsByArticleId(100L)).thenReturn(List.of(7L, 8L));
        when(articleTagMapper.selectTagIdsByArticleId(200L)).thenReturn(List.of(8L));
        when(articleTagMapper.selectTagIdsByArticleId(201L)).thenReturn(List.of(99L));
        when(articleTagMapper.selectTagIdsByArticleId(202L)).thenReturn(List.of(8L));
        when(accessService.canViewArticle(7L, current)).thenReturn(true);
        when(accessService.canViewArticle(7L, best)).thenReturn(true);
        when(accessService.canViewArticle(7L, backup)).thenReturn(true);
        when(accessService.canViewArticle(7L, hidden)).thenReturn(false);

        ArticleRecommendScenarioDefinition definition = new ArticleRecommendScenarioDefinition(
                articleMapper,
                articleTagMapper,
                accessService,
                new AiRuntimeProperties(),
                featureToggleService
        );
        ArticleRecommendScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.ARTICLE_RECOMMEND,
                new ArticleRecommendScenarioRequest(100L, "article", 7L, "帮我推荐两篇相关的", 3)
        )).data();

        assertNotNull(result.traceId());
        assertEquals("rule", result.sourceType());
        assertEquals(2, result.items().size());
        assertEquals(200L, result.items().get(0).articleId());
        assertEquals(201L, result.items().get(1).articleId());
        assertTrue(result.reason().contains("规则召回"));
    }

    @Test
    void shouldReturnEmptyResultWhenNoVisibleCandidatesExist() {
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
        AccessService accessService = mock(AccessService.class);
        FeatureToggleService featureToggleService = enabledFeatures();

        when(articleMapper.selectById(100L)).thenReturn(article(100L, "当前文章", 9L, 0, 1, 0));
        when(articleMapper.selectList(any())).thenReturn(List.of(article(100L, "当前文章", 9L, 0, 1, 0)));
        when(articleTagMapper.selectTagIdsByArticleId(100L)).thenReturn(List.of(7L));

        ArticleRecommendScenarioDefinition definition = new ArticleRecommendScenarioDefinition(
                articleMapper,
                articleTagMapper,
                accessService,
                new AiRuntimeProperties(),
                featureToggleService
        );
        ArticleRecommendScenarioResult result = definition.execute(AiScenarioRequest.of(
                AiScenarioCode.ARTICLE_RECOMMEND,
                new ArticleRecommendScenarioRequest(100L, "article", 7L, "推荐一下", 3)
        )).data();

        assertEquals(List.of(), result.items());
        assertEquals("rule", result.sourceType());
        assertTrue(result.reason().contains("暂无"));
    }

    private Article article(Long id, String title, Long categoryId, Integer visibility, Integer status, Integer isRecommend) {
        Article article = new Article();
        article.setId(id);
        article.setTitle(title);
        article.setCategoryId(categoryId);
        article.setVisibility(visibility);
        article.setStatus(status);
        article.setIsRecommend(isRecommend);
        article.setSummary(title + "摘要");
        return article;
    }

    private FeatureToggleService enabledFeatures() {
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isEnabled(RuntimeFeatureEnum.AI_ARTICLE_RECOMMEND)).thenReturn(true);
        return featureToggleService;
    }
}
