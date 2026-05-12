package com.chen404.service.support.scenario.recommend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleTag;
import com.chen404.mapper.ArticleMapper;
import com.chen404.mapper.ArticleTagMapper;
import com.chen404.service.AccessService;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioDefinition;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章推荐场景定义。
 * <p>
 * 第一阶段仅提供稳定接口形态，后续再接候选召回、排序与 AI rerank。
 */
@Component
public class ArticleRecommendScenarioDefinition implements AiScenarioDefinition<ArticleRecommendScenarioRequest, ArticleRecommendScenarioResult> {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final AccessService accessService;
    private final AiRuntimeProperties aiRuntimeProperties;

    public ArticleRecommendScenarioDefinition(
            ArticleMapper articleMapper,
            ArticleTagMapper articleTagMapper,
            AccessService accessService,
            AiRuntimeProperties aiRuntimeProperties) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.accessService = accessService;
        this.aiRuntimeProperties = aiRuntimeProperties;
    }

    @Override
    public AiScenarioCode code() {
        return AiScenarioCode.ARTICLE_RECOMMEND;
    }

    @Override
    public AiScenarioResult<ArticleRecommendScenarioResult> execute(AiScenarioRequest<ArticleRecommendScenarioRequest> request) {
        if (!aiRuntimeProperties.getRecommend().isEnabled()) {
            return AiScenarioResult.of(new ArticleRecommendScenarioResult(
                    List.of(),
                    "相关文章推荐能力未开启。",
                    "trace_" + UUID.randomUUID().toString().replace("-", ""),
                    "rule"
            ));
        }
        ArticleRecommendScenarioRequest payload = request.payload();
        List<ArticleRecommendScenarioItem> items = recommend(payload);
        String reason = items.isEmpty()
                ? "规则召回下暂无可推荐文章。"
                : "规则召回已返回站内相关文章。";
        return AiScenarioResult.of(new ArticleRecommendScenarioResult(
                items,
                reason,
                "trace_" + UUID.randomUUID().toString().replace("-", ""),
                "rule"
        ));
    }

    private List<ArticleRecommendScenarioItem> recommend(ArticleRecommendScenarioRequest request) {
        AiRuntimeProperties.Recommend recommendProperties = aiRuntimeProperties.getRecommend();
        int safeLimit = request.limit() <= 0 ? recommendProperties.getDefaultLimit() : request.limit();
        Article currentArticle = request.currentArticleId() == null ? null : articleMapper.selectById(request.currentArticleId());
        List<Long> currentTagIds = currentArticle == null
                ? List.of()
                : articleTagMapper.selectTagIdsByArticleId(currentArticle.getId());

        List<Article> candidates = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getIsRecommend)
                .orderByDesc(Article::getPublishTime)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + recommendProperties.getScanLimit()));
        Map<Long, List<Long>> candidateTagMap = loadCandidateTagMap(candidates);

        List<ScoredArticle> scored = new ArrayList<>();
        for (Article candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (request.currentArticleId() != null && Objects.equals(candidate.getId(), request.currentArticleId())) {
                continue;
            }
            if (!accessService.canViewArticle(request.requesterId(), candidate)) {
                continue;
            }
            List<Long> candidateTagIds = candidateTagMap.getOrDefault(candidate.getId(), List.of());
            int score = scoreCandidate(candidate, currentArticle, currentTagIds, candidateTagIds, request.seedText());
            if (score <= 0) {
                continue;
            }
            scored.add(new ScoredArticle(candidate, score, buildReason(candidate, currentArticle, currentTagIds, candidateTagIds)));
        }

        return scored.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .limit(safeLimit)
                .map(item -> new ArticleRecommendScenarioItem(
                        item.article().getId(),
                        item.article().getTitle(),
                        item.reason(),
                        "/article/" + item.article().getId()
                ))
                .collect(Collectors.toList());
    }

    private Map<Long, List<Long>> loadCandidateTagMap(List<Article> candidates) {
        List<Long> articleIds = candidates.stream()
                .filter(Objects::nonNull)
                .map(Article::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (articleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ArticleTag> articleTags = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getArticleId, articleIds));
        Map<Long, List<Long>> tagMap = new LinkedHashMap<>();
        for (ArticleTag articleTag : articleTags) {
            if (articleTag == null || articleTag.getArticleId() == null || articleTag.getTagId() == null) {
                continue;
            }
            tagMap.computeIfAbsent(articleTag.getArticleId(), key -> new ArrayList<>()).add(articleTag.getTagId());
        }
        return tagMap;
    }

    private int scoreCandidate(
            Article candidate,
            Article currentArticle,
            List<Long> currentTagIds,
            List<Long> candidateTagIds,
            String seedText) {
        int score = 0;
        if (currentArticle != null && Objects.equals(candidate.getCategoryId(), currentArticle.getCategoryId())) {
            score += 40;
        }
        long sharedTagCount = candidateTagIds.stream()
                .filter(new LinkedHashSet<>(currentTagIds)::contains)
                .count();
        score += (int) sharedTagCount * 25;
        if (Objects.equals(candidate.getIsRecommend(), 1)) {
            score += 15;
        }
        score += keywordScore(candidate, seedText);
        return score;
    }

    private String buildReason(
            Article candidate,
            Article currentArticle,
            List<Long> currentTagIds,
            List<Long> candidateTagIds) {
        List<String> reasons = new ArrayList<>();
        if (currentArticle != null && Objects.equals(candidate.getCategoryId(), currentArticle.getCategoryId())) {
            reasons.add("同分类");
        }
        long sharedTagCount = candidateTagIds.stream()
                .filter(new LinkedHashSet<>(currentTagIds)::contains)
                .count();
        if (sharedTagCount > 0) {
            reasons.add("共享标签");
        }
        if (Objects.equals(candidate.getIsRecommend(), 1)) {
            reasons.add("站点推荐");
        }
        return reasons.isEmpty() ? "规则匹配" : String.join(" / ", reasons);
    }

    private int keywordScore(Article candidate, String seedText) {
        if (!StringUtils.hasText(seedText)) {
            return 0;
        }
        String haystack = ((candidate.getTitle() == null ? "" : candidate.getTitle()) + "\n"
                + (candidate.getSummary() == null ? "" : candidate.getSummary()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : normalizeKeywords(seedText)) {
            if (haystack.contains(token)) {
                score += Math.min(12, Math.max(4, token.length() * 2));
            }
        }
        return score;
    }

    private List<String> normalizeKeywords(String seedText) {
        if (!StringUtils.hasText(seedText)) {
            return List.of();
        }
        return List.of(seedText.toLowerCase(Locale.ROOT)
                        .replace("帮我", " ")
                        .replace("推荐", " ")
                        .replace("两篇", " ")
                        .replace("相关的", " ")
                        .replace("文章", " ")
                        .split("[\\p{Punct}，。！？；：、“”‘’（）【】《》\\s]+"))
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .collect(Collectors.toList());
    }

    private record ScoredArticle(
            Article article,
            int score,
            String reason
    ) {
    }
}
