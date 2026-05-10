package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.ArticleAiChunk;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.ArticleAiChunkMapper;
import com.chen404.mapper.ArticleMapper;
import com.chen404.service.AccessService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文章知识切片服务实现。
 * <p>
 * 第一阶段优先使用 MySQL 切片表承载站内知识检索，
 * 先做到“可同步、可过滤、可引用”，后续再演进向量召回。
 */
@Service
public class ArticleKnowledgeServiceImpl implements ArticleKnowledgeService {

    private static final Pattern MARKDOWN_DECORATION = Pattern.compile("[#>*`~\\[\\]()-]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_WORD_SEPARATOR = Pattern.compile("[\\p{Punct}，。！？；：、“”‘’（）【】《》\\s]+");
    private static final int CONTENT_CHUNK_SIZE = 360;
    private static final int CONTENT_OVERLAP_SIZE = 80;
    private static final int SEARCH_SCAN_LIMIT = 80;
    private static final int KEYWORD_LIMIT = 8;
    private static final String CHUNK_TYPE_TITLE = "title";
    private static final String CHUNK_TYPE_SUMMARY = "summary";
    private static final String CHUNK_TYPE_CONTENT = "content";

    private final ArticleMapper articleMapper;
    private final ArticleAiChunkMapper articleAiChunkMapper;
    private final AccessService accessService;

    public ArticleKnowledgeServiceImpl(
            ArticleMapper articleMapper,
            ArticleAiChunkMapper articleAiChunkMapper,
            AccessService accessService) {
        this.articleMapper = articleMapper;
        this.articleAiChunkMapper = articleAiChunkMapper;
        this.accessService = accessService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncArticleChunks(Long articleId) {
        if (articleId == null) {
            throw new BadRequestException("文章 ID 不能为空");
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null || Objects.equals(article.getDeleted(), 1)) {
            removeArticleChunks(articleId);
            return;
        }

        articleAiChunkMapper.delete(new LambdaQueryWrapper<ArticleAiChunk>()
                .eq(ArticleAiChunk::getArticleId, articleId));

        List<ArticleAiChunk> chunks = buildChunks(article);
        for (ArticleAiChunk chunk : chunks) {
            articleAiChunkMapper.insert(chunk);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeArticleChunks(Long articleId) {
        if (articleId == null) {
            return;
        }
        articleAiChunkMapper.delete(new LambdaQueryWrapper<ArticleAiChunk>()
                .eq(ArticleAiChunk::getArticleId, articleId));
    }

    @Override
    public List<ArticleKnowledgeHit> searchVisibleChunks(String query, Long requesterId, Long currentArticleId, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        LinkedHashSet<ArticleKnowledgeHit> orderedHits = new LinkedHashSet<>();

        if (currentArticleId != null) {
            orderedHits.addAll(searchCurrentArticleChunks(currentArticleId, query, safeLimit));
        }

        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return orderedHits.stream().limit(safeLimit).collect(Collectors.toList());
        }

        LambdaQueryWrapper<ArticleAiChunk> wrapper = new LambdaQueryWrapper<>();
        for (String keyword : keywords) {
            wrapper.or(w -> w.like(ArticleAiChunk::getArticleTitle, keyword)
                    .or()
                    .like(ArticleAiChunk::getContentChunk, keyword));
        }
        wrapper.orderByDesc(ArticleAiChunk::getPublishTime)
                .last("LIMIT " + SEARCH_SCAN_LIMIT);

        List<ArticleAiChunk> candidates = articleAiChunkMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return orderedHits.stream().limit(safeLimit).collect(Collectors.toList());
        }

        Map<Long, Article> articlesById = articleMapper.selectBatchIds(candidates.stream()
                        .map(ArticleAiChunk::getArticleId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(Article::getId, article -> article));

        List<ArticleKnowledgeHit> rescored = new ArrayList<>();
        for (ArticleAiChunk chunk : candidates) {
            Article article = articlesById.get(chunk.getArticleId());
            if (article == null || !accessService.canViewArticle(requesterId, article)) {
                continue;
            }
            int score = scoreChunk(chunk, keywords, currentArticleId);
            if (score <= 0) {
                continue;
            }
            rescored.add(new ArticleKnowledgeHit(
                    article.getId(),
                    chunk.getArticleTitle(),
                    truncate(chunk.getContentChunk(), 180),
                    "/article/" + article.getId(),
                    score,
                    Objects.equals(article.getId(), currentArticleId)
            ));
        }

        rescored.stream()
                .sorted(Comparator.comparingInt(ArticleKnowledgeHit::score).reversed())
                .forEach(orderedHits::add);
        return orderedHits.stream().limit(safeLimit).collect(Collectors.toList());
    }

    private List<ArticleAiChunk> buildChunks(Article article) {
        List<ArticleAiChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        if (StringUtils.hasText(article.getTitle())) {
            chunks.add(buildChunk(article, chunkIndex++, CHUNK_TYPE_TITLE, article.getTitle().trim()));
        }

        if (StringUtils.hasText(article.getSummary())) {
            chunks.add(buildChunk(article, chunkIndex++, CHUNK_TYPE_SUMMARY, normalizePlainText(article.getSummary())));
        }

        String normalizedContent = normalizePlainText(article.getContent());
        if (!StringUtils.hasText(normalizedContent)) {
            return chunks;
        }

        List<String> paragraphs = splitParagraphs(normalizedContent);
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() > 0 && current.length() + paragraph.length() + 1 > CONTENT_CHUNK_SIZE) {
                chunks.add(buildChunk(article, chunkIndex++, CHUNK_TYPE_CONTENT, current.toString().trim()));
                current = new StringBuilder(overlapTail(current.toString(), CONTENT_OVERLAP_SIZE));
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            chunks.add(buildChunk(article, chunkIndex, CHUNK_TYPE_CONTENT, current.toString().trim()));
        }
        return chunks;
    }

    private ArticleAiChunk buildChunk(Article article, int chunkIndex, String chunkType, String contentChunk) {
        ArticleAiChunk chunk = new ArticleAiChunk();
        chunk.setArticleId(article.getId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkType(chunkType);
        chunk.setArticleTitle(article.getTitle());
        chunk.setContentChunk(contentChunk);
        chunk.setStatus(article.getStatus());
        chunk.setVisibility(article.getVisibility());
        chunk.setPublishTime(article.getPublishTime());
        return chunk;
    }

    private List<ArticleKnowledgeHit> searchCurrentArticleChunks(Long currentArticleId, String query, int limit) {
        List<ArticleAiChunk> chunks = articleAiChunkMapper.selectList(new LambdaQueryWrapper<ArticleAiChunk>()
                .eq(ArticleAiChunk::getArticleId, currentArticleId)
                .orderByAsc(ArticleAiChunk::getChunkIndex)
                .last("LIMIT " + Math.max(limit, 5)));
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<String> keywords = extractKeywords(query);
        List<ArticleKnowledgeHit> hits = new ArrayList<>();
        for (ArticleAiChunk chunk : chunks) {
            int score = keywords.isEmpty() ? 1 : scoreChunk(chunk, keywords, currentArticleId);
            if (score <= 0 && !keywords.isEmpty()) {
                continue;
            }
            hits.add(new ArticleKnowledgeHit(
                    chunk.getArticleId(),
                    chunk.getArticleTitle(),
                    truncate(chunk.getContentChunk(), 180),
                    "/article/" + chunk.getArticleId(),
                    score + 120,
                    true
            ));
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(ArticleKnowledgeHit::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int scoreChunk(ArticleAiChunk chunk, List<String> keywords, Long currentArticleId) {
        String haystack = (chunk.getArticleTitle() + "\n" + chunk.getContentChunk()).toLowerCase(Locale.ROOT);
        int score = Objects.equals(chunk.getArticleId(), currentArticleId) ? 80 : 0;
        score += CHUNK_TYPE_TITLE.equals(chunk.getChunkType()) ? 20 : 0;
        score += CHUNK_TYPE_SUMMARY.equals(chunk.getChunkType()) ? 12 : 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (haystack.contains(lowerKeyword)) {
                score += Math.min(18, Math.max(6, lowerKeyword.length() * 2));
            }
        }
        return score;
    }

    private List<String> extractKeywords(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT)
                .replace("帮我", " ")
                .replace("一下", " ")
                .replace("这篇", " ")
                .replace("文章", " ")
                .replace("站内", " ")
                .replace("博客", " ")
                .replace("给我", " ")
                .replace("看看", " ")
                .replace("什么", " ")
                .replace("一下子", " ");
        String[] parts = NON_WORD_SEPARATOR.split(normalized);
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String token = part.trim();
            if (token.length() >= 2 && token.length() <= 12) {
                keywords.add(token);
            }
            if (containsChinese(token) && token.length() > 4) {
                keywords.addAll(buildChineseNgrams(token));
            }
            if (keywords.size() >= KEYWORD_LIMIT) {
                break;
            }
        }
        return keywords.stream().limit(KEYWORD_LIMIT).collect(Collectors.toList());
    }

    private boolean containsChinese(String text) {
        return text.codePoints().anyMatch(code -> code >= 0x4E00 && code <= 0x9FFF);
    }

    private List<String> buildChineseNgrams(String text) {
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        int maxLen = Math.min(4, text.length());
        for (int n = 2; n <= maxLen; n++) {
            for (int i = 0; i <= text.length() - n; i++) {
                grams.add(text.substring(i, i + n));
                if (grams.size() >= KEYWORD_LIMIT) {
                    return new ArrayList<>(grams);
                }
            }
        }
        return new ArrayList<>(grams);
    }

    private List<String> splitParagraphs(String normalizedContent) {
        List<String> segments = new ArrayList<>();
        for (String raw : normalizedContent.split("\\n{2,}")) {
            String segment = raw.trim();
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            if (segment.length() <= CONTENT_CHUNK_SIZE) {
                segments.add(segment);
                continue;
            }
            int cursor = 0;
            while (cursor < segment.length()) {
                int end = Math.min(segment.length(), cursor + CONTENT_CHUNK_SIZE);
                segments.add(segment.substring(cursor, end));
                if (end >= segment.length()) {
                    break;
                }
                cursor = Math.max(0, end - CONTENT_OVERLAP_SIZE);
            }
        }
        return segments;
    }

    private String overlapTail(String content, int overlapLength) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.length() <= overlapLength) {
            return normalized;
        }
        return normalized.substring(normalized.length() - overlapLength);
    }

    private String normalizePlainText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String text = MARKDOWN_DECORATION.matcher(rawText).replaceAll(" ");
        text = text.replace("\r", "\n");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        return text.trim();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
