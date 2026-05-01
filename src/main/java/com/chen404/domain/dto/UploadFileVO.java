package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "上传文件结果")
@Data
public class UploadFileVO {

    @Schema(description = "文件访问地址", example = "https://cdn.example.com/upload/demo.png")
    private String url;

    @Schema(description = "文件名", example = "demo.png")
    private String name;

    @Schema(description = "文件大小，单位字节", example = "10240")
    private String size;
}
