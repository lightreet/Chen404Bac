package com.chen404.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改阅读笔记命令；原文锚点保持不可变，避免编辑感悟时意外漂移。
 */
@Data
public class ReaderNoteUpdateCommand {

    @Size(max = 2000, message = "感悟不能超过 2000 个字符")
    private String reflection;

    @NotBlank(message = "请选择高亮颜色")
    private String highlightColor;
}
