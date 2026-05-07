package com.chen404.service.support;

/**
 * 通用大模型文本客户端。
 * <p>
 * 屏蔽 OpenAI-compatible 上游在 chat/completions 与 responses 协议上的差异，
 * 业务层只依赖“给提示词，拿文本结果”的统一抽象。
 */
public interface LlmClient {

    /**
     * 调用上游 LLM 生成文本。
     *
     * @param request 文本生成请求
     * @return 上游返回的纯文本内容
     */
    String generateText(LlmTextRequest request);
}
