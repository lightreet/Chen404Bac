package com.chen404.service.support.scenario.music;

/**
 * 音乐曲目补全场景请求。
 *
 * @param title       歌名
 * @param artist      已知歌手，可为空
 * @param album       已知专辑，可为空
 * @param releaseYear 已知发行年份，可为空
 * @param language    已知语言，可为空
 * @param genre       已知风格，可为空
 * @param lyrics      已知歌词内容，可为空
 * @param limit       返回候选数量，可为空
 */
public record MusicTrackSuggestScenarioRequest(
        String title,
        String artist,
        String album,
        Integer releaseYear,
        String language,
        String genre,
        String lyrics,
        Integer limit
) {
}
