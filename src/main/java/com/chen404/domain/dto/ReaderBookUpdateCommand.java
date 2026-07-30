package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReaderBookUpdateCommand {

    @NotBlank(message = "书名不能为空")
    @Size(max = 255, message = "书名不能超过 255 个字符")
    private String title;

    @Size(max = 255, message = "作者不能超过 255 个字符")
    private String author;

    @Size(max = 4000, message = "简介不能超过 4000 个字符")
    private String description;

    @Schema(description = "用户上传的自定义封面文件 ID")
    private Long coverFileId;

    @NotBlank(message = "请选择书籍可见范围")
    @Pattern(regexp = "(?i)public|private", message = "书籍可见范围仅支持 public 或 private")
    private String visibility;
}
