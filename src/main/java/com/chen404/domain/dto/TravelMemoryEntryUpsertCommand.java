package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行记忆照片保存命令对象。
 */
@Schema(description = "旅行记忆照片保存命令对象")
@Data
public class TravelMemoryEntryUpsertCommand {

    @Schema(description = "照片条目 ID，编辑已有照片时传入", example = "2001")
    private Long id;

    @Schema(description = "图片地址", example = "https://cdn.example.com/travel/gz-park-1.jpg")
    @NotBlank(message = "图片地址不能为空")
    @Size(max = 500, message = "图片地址长度不能超过 500 个字符")
    private String imageUrl;

    @Schema(description = "图片备注", example = "湖面和桥")
    @Size(max = 255, message = "图片备注长度不能超过 255 个字符")
    private String remark;

    @Schema(description = "图片感想", example = "清晨的风把水面吹得很轻。")
    @Size(max = 2000, message = "图片感想长度不能超过 2000 个字符")
    private String thanksNote;

    @Schema(description = "拍摄时间")
    private LocalDateTime shotAt;

    @Schema(description = "显示顺序", example = "0")
    private Integer displayOrder;

    @Schema(description = "是否作为整趟旅行封面", example = "true")
    private Boolean cover;

    @Schema(description = "是否作为片段封面", example = "true")
    private Boolean stopCover;

    @Schema(description = "照片原始纬度", example = "23.129110")
    private BigDecimal sourceLatitude;

    @Schema(description = "照片原始经度", example = "113.264385")
    private BigDecimal sourceLongitude;

    @Schema(description = "坐标来源：NONE / EXIF / MANUAL", example = "EXIF")
    private String geoSource;
}
