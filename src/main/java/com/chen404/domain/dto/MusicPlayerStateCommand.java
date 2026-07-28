package com.chen404.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录用户音乐播放现场保存命令。
 */
@Data
public class MusicPlayerStateCommand {

    public static final int MAX_QUEUE_SIZE = 200;

    @NotNull
    @Size(max = MAX_QUEUE_SIZE)
    private List<@NotNull @Positive Long> trackIds = new ArrayList<>();

    @Positive
    private Long currentTrackId;

    @DecimalMin("0.0")
    private Double currentTime = 0D;

    @Pattern(regexp = "sequence|shuffle|single")
    private String mode = "sequence";
}
