package com.chen404.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReaderPreferenceCommand {

    @Min(14)
    @Max(32)
    private Integer fontSize;

    @DecimalMin("1.30")
    @DecimalMax("2.60")
    private BigDecimal lineHeight;

    @Min(520)
    @Max(1100)
    private Integer contentWidth;

    @Min(8)
    @Max(40)
    private Integer paragraphSpacing;

    @Pattern(regexp = "light|rose|dark", message = "阅读主题不合法")
    private String theme;

    @Pattern(regexp = "serif|sans", message = "阅读字体不合法")
    private String fontFamily;
}
