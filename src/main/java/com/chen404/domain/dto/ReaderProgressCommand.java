package com.chen404.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReaderProgressCommand {

    @NotNull(message = "章节不能为空")
    private Long chapterId;

    @Min(value = 0, message = "正文块序号不能小于 0")
    @NotNull(message = "正文块序号不能为空")
    private Integer blockIndex = 0;

    @Min(value = 0, message = "字符偏移不能小于 0")
    @NotNull(message = "字符偏移不能为空")
    private Integer characterOffset = 0;

    @NotNull(message = "阅读进度不能为空")
    @DecimalMin(value = "0", message = "阅读进度不能小于 0")
    @DecimalMax(value = "100", message = "阅读进度不能超过 100")
    private BigDecimal progressPercent;

    @Size(max = 255, message = "位置上下文不能超过 255 个字符")
    private String locatorContext;

    private Boolean finished = false;
}
