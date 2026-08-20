package com.chen404.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReaderPreferenceVO {

    private Integer fontSize;
    private BigDecimal lineHeight;
    private Integer contentWidth;
    private Integer paragraphSpacing;
    private String theme;
    private String fontFamily;
    private String readingMode;
}
