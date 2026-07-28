package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录用户可在有效期内恢复的音乐播放现场。
 */
@Schema(description = "音乐播放现场")
@Data
public class MusicPlayerStateVO {

    private List<Long> trackIds = new ArrayList<>();
    private Long currentTrackId;
    private Double currentTime;
    private String mode;
    private Instant updatedAt;
}
