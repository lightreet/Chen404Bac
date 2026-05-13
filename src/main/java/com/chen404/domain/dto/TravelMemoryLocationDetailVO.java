package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行纪念地点详情视图对象。
 */
@Schema(description = "旅行纪念地点详情视图对象")
@Data
public class TravelMemoryLocationDetailVO {

    @Schema(description = "地点 ID", example = "1001")
    private Long id;

    @Schema(description = "地点标题", example = "成都春日散记")
    private String title;

    @Schema(description = "省份", example = "四川")
    private String province;

    @Schema(description = "城市", example = "成都")
    private String city;

    @Schema(description = "展示纬度", example = "30.572815")
    private BigDecimal latitude;

    @Schema(description = "展示经度", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "地点简介", example = "把同一座城市里几段不同的散步记在一起。")
    private String summaryNote;

    @Schema(description = "封面图地址", example = "https://cdn.example.com/travel/chengdu-cover.jpg")
    private String coverImage;

    @Schema(description = "到访时间")
    private LocalDateTime visitedAt;

    @Schema(description = "展示状态：0-隐藏 1-展示", example = "1")
    private Integer status;

    @Schema(description = "排序值", example = "10")
    private Integer sortOrder;

    @Schema(description = "照片数量", example = "3")
    private Integer entryCount;

    @Schema(description = "照片列表")
    private List<TravelMemoryEntryVO> entries;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
