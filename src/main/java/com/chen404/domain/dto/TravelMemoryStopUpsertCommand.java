package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行记忆片段保存命令对象。
 */
@Schema(description = "旅行记忆片段保存命令对象")
@Data
public class TravelMemoryStopUpsertCommand {

    @Schema(description = "片段 ID，编辑已有片段时传入", example = "3001")
    private Long id;

    @Schema(description = "片段标题", example = "凌晨两点的街道")
    @NotBlank(message = "片段标题不能为空")
    @Size(max = 120, message = "片段标题长度不能超过 120 个字符")
    private String title;

    @Schema(description = "片段文字", example = "夜里的路灯和摊位声音，让这座城市换了一种语气。")
    @Size(max = 1000, message = "片段文字长度不能超过 1000 个字符")
    private String storyNote;

    @Schema(description = "片段日期")
    private LocalDateTime visitedAt;

    @Schema(description = "片段纬度", example = "23.129110")
    private BigDecimal latitude;

    @Schema(description = "片段经度", example = "113.264385")
    private BigDecimal longitude;

    @Schema(description = "片段排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "片段下的照片列表")
    @Valid
    @NotEmpty(message = "每个片段至少需要保留一张照片")
    private List<TravelMemoryEntryUpsertCommand> entries;
}
