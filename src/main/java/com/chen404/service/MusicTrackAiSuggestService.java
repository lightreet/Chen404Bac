package com.chen404.service;

import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;

/**
 * 音乐曲目信息 AI 补全能力。
 */
public interface MusicTrackAiSuggestService {

    /**
     * 根据歌名和可选歌手生成曲目表单补全建议。
     *
     * @param request 补全请求
     * @return 曲目信息建议
     */
    MusicTrackAiSuggestResponse suggest(MusicTrackAiSuggestRequest request);
}
