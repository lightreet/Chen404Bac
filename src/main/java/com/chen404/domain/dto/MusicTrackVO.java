package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "音乐曲目视图对象")
@Data
public class MusicTrackVO {

    private Long id;
    private String title;
    private String artist;
    private String album;
    private Integer releaseYear;
    private String language;
    private String genre;
    private List<String> tags;
    private Long audioFileId;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
