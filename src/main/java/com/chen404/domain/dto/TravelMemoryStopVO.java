package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行记忆片段视图对象。
 */
@Schema(description = "旅行记忆片段视图对象")
@Data
public class TravelMemoryStopVO {

    @Schema(description = "片段 ID", example = "3001")
    private Long id;

    @Schema(description = "片段标题", example = "凌晨两点的街道")
    private String title;

    @Schema(description = "片段文字", example = "夜里的路灯和摊位声音，让这座城市换了一种语气。")
    private String storyNote;

    @Schema(description = "片段封面图", example = "https://cdn.example.com/travel/gz-night-cover.jpg")
    private String coverImage;

    @Schema(description = "片段日期")
    private LocalDateTime visitedAt;

    @Schema(description = "片段纬度", example = "23.129110")
    private BigDecimal latitude;

    @Schema(description = "片段经度", example = "113.264385")
    private BigDecimal longitude;

    @Schema(description = "片段排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "片段照片数量", example = "2")
    private Integer entryCount;

    @Schema(description = "片段照片列表")
    private List<TravelMemoryEntryVO> entries;
}
