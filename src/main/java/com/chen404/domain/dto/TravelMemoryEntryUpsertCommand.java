package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行纪念照片保存命令对象。
 */
@Schema(description = "旅行纪念照片保存命令对象")
@Data
public class TravelMemoryEntryUpsertCommand {

    @Schema(description = "照片条目 ID，编辑已有照片时传入", example = "2001")
    private Long id;

    @Schema(description = "图片地址", example = "https://cdn.example.com/travel/chengdu-1.jpg")
    @NotBlank(message = "图片地址不能为空")
    @Size(max = 500, message = "图片地址长度不能超过 500 个字符")
    private String imageUrl;

    @Schema(description = "图片备注", example = "傍晚刚下过雨，街边灯光很好看")
    @Size(max = 255, message = "图片备注长度不能超过 255 个字符")
    private String remark;

    @Schema(description = "图片感想", example = "这次旅程最像电影里的一个晚上。")
    @Size(max = 2000, message = "图片感想长度不能超过 2000 个字符")
    private String thanksNote;

    @Schema(description = "拍摄时间")
    private LocalDateTime shotAt;

    @Schema(description = "展示顺序", example = "0")
    private Integer displayOrder;

    @Schema(description = "是否作为地点封面", example = "true")
    private Boolean cover;

    @Schema(description = "照片原始纬度", example = "30.572815")
    private BigDecimal sourceLatitude;

    @Schema(description = "照片原始经度", example = "104.066801")
    private BigDecimal sourceLongitude;

    @Schema(description = "坐标来源：NONE / EXIF / MANUAL", example = "EXIF")
    private String geoSource;
}
