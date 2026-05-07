package com.chen404.service;

import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;

/**
 * 文章 AI 辅助能力接口。
 * <p>
 * 负责为文章编辑页提供摘要、标签等结构化建议。
 */
public interface AiArticleAssistService {

    /**
     * 根据文章标题和正文生成 AI 建议。
     *
     * @param request 文章辅助生成请求
     * @return 摘要与标签建议
     */
    AiArticleAssistResponse generateAssist(AiArticleAssistRequest request);
}
