package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 单文件上传表单 DTO。
 */
@Schema(description = "单文件上传表单")
@Data
public class SingleFileUploadDTO {

    @Schema(description = "待上传文件", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile file;
}
