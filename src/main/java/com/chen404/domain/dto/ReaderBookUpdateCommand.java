package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
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
}
