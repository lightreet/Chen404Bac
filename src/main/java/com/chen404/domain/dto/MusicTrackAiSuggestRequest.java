package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 音乐曲目信息 AI 补全请求。
 */
@Data
public class MusicTrackAiSuggestRequest {

    @NotBlank(message = "歌名不能为空")
    private String title;

    private String artist;

    private String album;

    private Integer releaseYear;

    private String language;

    private String genre;

    private String lyrics;

    private Integer limit;
}
