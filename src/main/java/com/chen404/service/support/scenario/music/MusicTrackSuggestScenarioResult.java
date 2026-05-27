package com.chen404.service.support.scenario.music;

import java.util.List;

/**
 * 音乐曲目补全场景结果。
 *
 * @param title          歌名
 * @param artist         歌手
 * @param album          专辑
 * @param releaseYear    发行年份
 * @param language       语言
 * @param genre          风格
 * @param tags           标签
 * @param recommendation 推荐语
 * @param moodText       氛围短句
 * @param lyricSource    歌词来源说明
 */
public record MusicTrackSuggestScenarioResult(
        String title,
        String artist,
        String album,
        Integer releaseYear,
        String language,
        String genre,
        List<String> tags,
        String recommendation,
        String moodText,
        String lyricSource
) {
}
