package com.chen404.service.support.scenario.chat;

import java.util.List;

/**
 * 女仆聊天场景结果。
 *
 * @param panelAnswer 聊天面板完整回答
 * @param bubbleText  人物旁短气泡文案
 * @param mood        情绪标签
 * @param suggestions 快捷建议
 */
public record MaidChatScenarioResult(
        String panelAnswer,
        String bubbleText,
        String mood,
        List<String> suggestions
) {
}
