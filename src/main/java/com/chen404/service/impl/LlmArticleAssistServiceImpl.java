package com.chen404.service.impl;

import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.service.AiArticleAssistService;
import com.chen404.service.FeatureToggleService;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import com.chen404.service.support.scenario.article.ArticleAssistScenarioRequest;
import com.chen404.service.support.scenario.article.ArticleAssistScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 通用 LLM 文章助手。
 * <p>
 * 当前负责根据文章标题与正文生成摘要和标签建议，
 * 底层大模型调用已下沉到通用 LLM 客户端，当前类只保留 prompt 构造与业务结果解析。
 */
@Service
public class LlmArticleAssistServiceImpl implements AiArticleAssistService {

    private static final Logger log = LoggerFactory.getLogger(LlmArticleAssistServiceImpl.class);

    private static final String EMPTY_RESULT_ERROR = "LLM 服务返回空结果";

    private final AiScenarioExecutor aiScenarioExecutor;
    private final FeatureToggleService featureToggleService;

    public LlmArticleAssistServiceImpl(
            AiScenarioExecutor aiScenarioExecutor,
            FeatureToggleService featureToggleService) {
        this.aiScenarioExecutor = aiScenarioExecutor;
        this.featureToggleService = featureToggleService;
    }

    @Override
    public AiArticleAssistResponse generateAssist(AiArticleAssistRequest request) {
        if (!featureToggleService.isEnabled(RuntimeFeatureEnum.AI_ARTICLE_ASSIST)) {
            throw new IllegalStateException("AI 文章助手当前已在管理后台关闭");
        }
        AiScenarioResult<ArticleAssistScenarioResult> scenarioExecution = aiScenarioExecutor.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.ARTICLE_ASSIST,
                        new ArticleAssistScenarioRequest(
                                request.getTitle(),
                                request.getContent(),
                                Boolean.TRUE.equals(request.getRegenerate()),
                                request.getCurrentSummary(),
                                request.getCurrentTags()
                        )
                )
        );
        ArticleAssistScenarioResult scenarioResult = scenarioExecution.data();
        AiArticleAssistResponse result = new AiArticleAssistResponse();
        result.setSummary(scenarioResult.summary());
        result.setTags(scenarioResult.tags());
        if (!StringUtils.hasText(result.getSummary()) && result.getTags().isEmpty()) {
            throw new IllegalStateException(EMPTY_RESULT_ERROR);
        }

        int summaryLength = result.getSummary() == null ? 0 : result.getSummary().length();
        log.info("LLM 生成完成，summaryLength={}, tagCount={}", summaryLength, result.getTags().size());
        return result;
    }
}
