package com.chen404.service.support;

/**
 * 通用 LLM 文本流式回调。
 * <p>
 * 业务层通过该回调接收模型增量文本，并在连接断开时终止上游读取。
 */
public interface LlmTextStreamHandler {

    /**
     * 是否已取消当前流式生成。
     *
     * @return true 表示应尽快中止上游读取
     */
    boolean isCancelled();

    /**
     * 接收一段新增文本。
     *
     * @param text 增量文本
     */
    void onTextDelta(String text);

    /**
     * 流式生成正常完成。
     */
    void onComplete();
}
