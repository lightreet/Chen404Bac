package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多文件上传表单 DTO。
 */
@Schema(description = "多文件上传表单")
@Data
public class MultiFileUploadDTO {

    @Schema(description = "待上传文件列表")
    @ArraySchema(
            schema = @Schema(type = "string", format = "binary"),
            arraySchema = @Schema(description = "待上传文件列表", requiredMode = Schema.RequiredMode.REQUIRED))
    private MultipartFile[] files;
}
