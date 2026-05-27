package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MusicPlaylistUpsertCommand {

    @NotBlank(message = "歌单名称不能为空")
    private String name;

    private String description;
    private Long coverFileId;
    private String coverUrl;
    private String openingText;
    private Boolean defaultPlaylist;
    private Boolean publicPlaylist;
    private Integer sortOrder;
}
