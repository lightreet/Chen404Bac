package com.chen404.service.support;

/** 上游流超过总时长或空闲时长，调用方应结束当前生成。 */
public class LlmStreamTimeoutException extends IllegalStateException {
    public LlmStreamTimeoutException() {
        super("LLM 流式响应超时，请稍后重试");
    }
}
