package com.chen404.domain.dto;

import com.chen404.domain.ArticleImageImportConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 文章导入只接受图片 URL，上传者和用途由服务端确定。 */
@Data
@Schema(description = "转存文章中的远程图片")
public class RemoteImageImportDTO {
    @NotBlank(message = "图片链接不能为空")
    @Size(max = ArticleImageImportConstraints.URL_MAX_LENGTH, message = "图片链接过长")
    @Schema(description = "公开的 HTTP(S) 图片链接")
    private String url;
}
