package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行记忆照片视图对象。
 */
@Schema(description = "旅行记忆照片视图对象")
@Data
public class TravelMemoryEntryVO {

    @Schema(description = "照片条目 ID", example = "2001")
    private Long id;

    @Schema(description = "图片地址", example = "https://cdn.example.com/travel/gz-park-1.jpg")
    private String imageUrl;

    @Schema(description = "图片备注", example = "湖面和桥")
    private String remark;

    @Schema(description = "图片感想", example = "清晨的风把水面吹得很轻。")
    private String thanksNote;

    @Schema(description = "拍摄时间")
    private LocalDateTime shotAt;

    @Schema(description = "显示顺序", example = "0")
    private Integer displayOrder;

    @Schema(description = "所属片段 ID", example = "3001")
    private Long stopId;

    @Schema(description = "是否作为整趟旅行封面", example = "true")
    private Boolean cover;

    @Schema(description = "是否作为片段封面", example = "true")
    private Boolean stopCover;

    @Schema(description = "照片原始纬度", example = "23.129110")
    private BigDecimal sourceLatitude;

    @Schema(description = "照片原始经度", example = "113.264385")
    private BigDecimal sourceLongitude;

    @Schema(description = "坐标来源", example = "EXIF")
    private String geoSource;
}
