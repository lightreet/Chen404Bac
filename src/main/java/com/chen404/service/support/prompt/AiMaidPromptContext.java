package com.chen404.service.support.prompt;

/**
 * AI 女仆 prompt 运行时上下文。
 * <p>
 * 该对象用于向 prompt builder 传入当前页面、文章与能力边界等运行时信息，
 * 避免在 service 内部拼接零散字符串。
 *
 * @param pageContext             当前页面上下文，例如 home/article/about
 * @param currentArticleId        当前文章 ID，若不在文章页则为空
 * @param currentArticleTitle     当前文章标题，若未知则为空
 * @param citationsRequired       是否要求回答携带引用
 * @param allowCasualConversation 是否允许切入轻量日常对话
 */
public record AiMaidPromptContext(
        String pageContext,
        Long currentArticleId,
        String currentArticleTitle,
        boolean citationsRequired,
        boolean allowCasualConversation
) {

    /**
     * 创建空上下文。
     *
     * @return 默认上下文
     */
    public static AiMaidPromptContext empty() {
        return new AiMaidPromptContext("", null, "", false, true);
    }
}
