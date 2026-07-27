package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "上传文件结果")
@Data
public class UploadFileVO {

    @Schema(description = "文件记录 ID", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "文件访问地址", example = "https://cdn.example.com/upload/demo.png")
    private String url;

    @Schema(description = "文件名", example = "demo.png")
    private String name;

    @Schema(description = "文件大小，单位字节", example = "10240")
    private String size;

    @Schema(description = "图片 EXIF 解析出的纬度", example = "30.572815")
    private BigDecimal latitude;

    @Schema(description = "图片 EXIF 解析出的经度", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "图片 EXIF 解析出的拍摄时间")
    private LocalDateTime shotAt;
}
