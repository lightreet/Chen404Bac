package com.chen404.service.support.scenario.music;

import java.util.List;

/**
 * 音乐曲目补全场景结果。
 *
 * @param candidates 候选结果
 */
public record MusicTrackSuggestScenarioResult(
        List<MusicTrackSuggestCandidate> candidates
) {
}
