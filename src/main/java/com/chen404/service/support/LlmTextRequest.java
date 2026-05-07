package com.chen404.service.support;

/**
 * 通用 LLM 文本生成请求。
 * <p>
 * 该对象用于承载业务层传给 LLM 客户端的统一参数，
 * 业务服务只需要描述提示词与可选覆盖项，不直接关心上游接口协议细节。
 */
public record LlmTextRequest(
        String model,
        String systemInstruction,
        String userPrompt,
        Double temperature,
        Integer maxTokens
) {

    /**
     * 创建使用默认模型与默认生成参数的文本请求。
     *
     * @param systemInstruction 系统提示词
     * @param userPrompt        用户提示词
     * @return 通用文本生成请求
     */
    public static LlmTextRequest of(String systemInstruction, String userPrompt) {
        return new LlmTextRequest(null, systemInstruction, userPrompt, null, null);
    }
}
