package com.chen404.service.support.scenario;

/**
 * AI 场景统一结果包装。
 *
 * @param data 场景结果数据
 * @param <T>  结果类型
 */
public record AiScenarioResult<T>(
        T data
) {

    public static <T> AiScenarioResult<T> of(T data) {
        return new AiScenarioResult<>(data);
    }
}
