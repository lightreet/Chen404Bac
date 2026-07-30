package com.chen404.domain.dto;

import lombok.Data;

/**
 * 小说导入前的可回填资料。
 *
 * <p>预解析结果只用于表单展示，不会创建书籍或保存原始文件。</p>
 */
@Data
public class ReaderBookPreviewVO {

    private String title;
    private String author;
    private String description;
    private String language;
    private String sourceFormat;
    private String sourceEncoding;
    private String coverDataUrl;
    private String coverFileName;
}
