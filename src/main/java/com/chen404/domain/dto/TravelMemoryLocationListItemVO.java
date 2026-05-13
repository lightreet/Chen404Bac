package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行纪念地点列表项视图对象。
 */
@Schema(description = "旅行纪念地点列表项视图对象")
@Data
public class TravelMemoryLocationListItemVO {

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

    @Schema(description = "照片数量", example = "3")
    private Integer entryCount;
}
