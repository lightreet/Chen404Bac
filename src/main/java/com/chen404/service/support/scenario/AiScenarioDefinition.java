package com.chen404.service.support.scenario;

/**
 * AI 场景定义。
 *
 * @param <I> 请求负载类型
 * @param <O> 结果数据类型
 */
public interface AiScenarioDefinition<I, O> {

    /**
     * 当前定义所处理的场景编码。
     *
     * @return 场景编码
     */
    AiScenarioCode code();

    /**
     * 执行场景。
     *
     * @param request 场景请求
     * @return 场景结果
     */
    AiScenarioResult<O> execute(AiScenarioRequest<I> request);
}
