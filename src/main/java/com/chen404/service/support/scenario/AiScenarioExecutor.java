package com.chen404.service.support.scenario;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AI 场景统一执行器。
 * <p>
 * 负责按场景编码路由到对应定义，让业务层只关心输入输出对象。
 */
@Component
public class AiScenarioExecutor {

    private final Map<AiScenarioCode, AiScenarioDefinition<?, ?>> definitions = new EnumMap<>(AiScenarioCode.class);

    public AiScenarioExecutor(List<AiScenarioDefinition<?, ?>> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        for (AiScenarioDefinition<?, ?> definition : definitions) {
            this.definitions.put(definition.code(), definition);
        }
    }

    @SuppressWarnings("unchecked")
    public <I, O> AiScenarioResult<O> execute(AiScenarioRequest<I> request) {
        AiScenarioDefinition<I, O> definition = (AiScenarioDefinition<I, O>) definitions.get(request.code());
        if (definition == null) {
            throw new IllegalArgumentException("未找到 AI 场景定义，code=" + request.code());
        }
        return definition.execute(request);
    }
}
