package com.chen404.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建阅读笔记命令。
 */
@Data
public class ReaderNoteCreateCommand {

    @NotNull(message = "章节不能为空")
    private Long chapterId;

    @NotNull(message = "选区起始正文块不能为空")
    @Min(value = 0, message = "选区起始正文块不能小于 0")
    private Integer startBlockIndex;

    @NotNull(message = "选区起始字符偏移不能为空")
    @Min(value = 0, message = "选区起始字符偏移不能小于 0")
    private Integer startCharacterOffset;

    @NotNull(message = "选区结束正文块不能为空")
    @Min(value = 0, message = "选区结束正文块不能小于 0")
    private Integer endBlockIndex;

    @NotNull(message = "选区结束字符偏移不能为空")
    @Min(value = 0, message = "选区结束字符偏移不能小于 0")
    private Integer endCharacterOffset;

    @NotBlank(message = "请选择要记录的原文")
    @Size(max = 5000, message = "单条笔记的原文片段不能超过 5000 个字符")
    private String excerpt;

    @Size(max = 2000, message = "感悟不能超过 2000 个字符")
    private String reflection;

    @NotBlank(message = "请选择高亮颜色")
    private String highlightColor;

    @Size(max = 255, message = "选区前文上下文不能超过 255 个字符")
    private String prefixContext;

    @Size(max = 255, message = "选区后文上下文不能超过 255 个字符")
    private String suffixContext;

    @NotNull(message = "正文版本不能为空")
    @Min(value = 1, message = "正文版本不能小于 1")
    private Integer contentVersion;
}
