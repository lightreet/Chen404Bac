package com.chen404.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐曲目信息 AI 补全结果。
 */
@Data
public class MusicTrackAiSuggestResponse {

    private String title;
    private String artist;
    private String album;
    private Integer releaseYear;
    private String language;
    private String genre;
    private List<String> tags = new ArrayList<>();
    private String recommendation;
    private String moodText;
    private String lyricSource;
}
