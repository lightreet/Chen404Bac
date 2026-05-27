package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MusicTrackUpsertCommand {

    @NotBlank(message = "歌名不能为空")
    private String title;

    @NotBlank(message = "歌手不能为空")
    private String artist;

    private String album;
    private Integer releaseYear;
    private String language;
    private String genre;
    private List<String> tags = new ArrayList<>();

    private Long audioFileId;

    @NotBlank(message = "音频地址不能为空")
    private String audioUrl;

    private Long coverFileId;
    private String coverUrl;
    private String lyricType;
    private String lyrics;
    private String lyricSource;
    private String recommendation;
    private String moodText;
    private String status;
    private Integer sortOrder;
}
