package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "音乐歌单视图对象")
@Data
public class MusicPlaylistVO {

    private Long id;
    private String name;
    private String description;
    private Long coverFileId;
    private String coverUrl;
    private String openingText;
    private Boolean defaultPlaylist;
    private Boolean publicPlaylist;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MusicTrackVO> tracks = new ArrayList<>();
}
