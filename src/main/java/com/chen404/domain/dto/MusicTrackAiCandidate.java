package com.chen404.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐曲目 AI 候选结果。
 */
@Data
public class MusicTrackAiCandidate {

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
    private String confidence;
    private String matchReason;
}
