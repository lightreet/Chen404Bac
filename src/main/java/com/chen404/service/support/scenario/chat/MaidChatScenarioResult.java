package com.chen404.service.support.scenario.chat;

import java.util.List;

/**
 * 女仆聊天场景结果。
 *
 * @param replyText    回复正文
 * @param mood         情绪标签
 * @param suggestions  快捷建议
 */
public record MaidChatScenarioResult(
        String replyText,
        String mood,
        List<String> suggestions
) {
}
