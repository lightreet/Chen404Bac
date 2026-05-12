package com.chen404.service.support.scenario;

import java.util.Objects;

/**
 * AI 场景统一请求包装。
 *
 * @param code    场景编码
 * @param payload 场景负载
 * @param <T>     负载类型
 */
public record AiScenarioRequest<T>(
        AiScenarioCode code,
        T payload
) {

    public AiScenarioRequest {
        Objects.requireNonNull(code, "AI 场景编码不能为空");
    }

    public static <T> AiScenarioRequest<T> of(AiScenarioCode code, T payload) {
        return new AiScenarioRequest<>(code, payload);
    }
}
