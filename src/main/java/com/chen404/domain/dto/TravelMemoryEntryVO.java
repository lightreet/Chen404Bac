package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行纪念照片视图对象。
 */
@Schema(description = "旅行纪念照片视图对象")
@Data
public class TravelMemoryEntryVO {

    @Schema(description = "照片条目 ID", example = "2001")
    private Long id;

    @Schema(description = "图片地址", example = "https://cdn.example.com/travel/chengdu-1.jpg")
    private String imageUrl;

    @Schema(description = "图片备注", example = "傍晚刚下过雨，街边灯光很好看")
    private String remark;

    @Schema(description = "图片感想", example = "这次旅程最像电影里的一个晚上。")
    private String thanksNote;

    @Schema(description = "拍摄时间")
    private LocalDateTime shotAt;

    @Schema(description = "展示顺序", example = "0")
    private Integer displayOrder;

    @Schema(description = "是否作为封面", example = "true")
    private Boolean cover;

    @Schema(description = "照片原始纬度", example = "30.572815")
    private BigDecimal sourceLatitude;

    @Schema(description = "照片原始经度", example = "104.066801")
    private BigDecimal sourceLongitude;

    @Schema(description = "坐标来源", example = "EXIF")
    private String geoSource;
}
