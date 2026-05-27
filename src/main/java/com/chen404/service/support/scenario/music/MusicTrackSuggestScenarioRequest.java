package com.chen404.service.support.scenario.music;

/**
 * 音乐曲目补全场景请求。
 *
 * @param title  歌名
 * @param artist 已知歌手，可为空
 */
public record MusicTrackSuggestScenarioRequest(
        String title,
        String artist
) {
}
