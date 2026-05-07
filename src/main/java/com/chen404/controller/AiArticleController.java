package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;
import com.chen404.service.AiArticleAssistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章 AI 辅助接口控制器。
 * <p>
 * 当前仅面向管理员开放，用于在编辑页内生成摘要与标签建议。
 */
@Tag(name = "文章 AI", description = "文章摘要与标签智能生成")
@RestController
public class AiArticleController {

    private static final Logger log = LoggerFactory.getLogger(AiArticleController.class);
    private static final int HTTP_BAD_REQUEST = 400;

    private final AiArticleAssistService aiArticleAssistService;

    public AiArticleController(AiArticleAssistService aiArticleAssistService) {
        this.aiArticleAssistService = aiArticleAssistService;
    }

    @RequireAdmin
    @Operation(summary = "生成文章摘要和标签", description = "根据文章标题和正文生成 AI 摘要与标签建议")
    @PostMapping("/articles/ai/assist")
    public Result<AiArticleAssistResponse> generateAssist(@Valid @RequestBody AiArticleAssistRequest request) {
        try {
            return Result.success(aiArticleAssistService.generateAssist(request));
        } catch (IllegalStateException e) {
            log.warn("生成文章 AI 建议失败，title={}", request.getTitle(), e);
            return Result.error(HTTP_BAD_REQUEST, e.getMessage());
        }
    }
}
