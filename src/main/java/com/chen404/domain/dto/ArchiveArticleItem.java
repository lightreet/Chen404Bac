package com.chen404.domain.dto;

import com.chen404.domain.entity.Tag;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 归档时间线中的单篇文章（仅必要字段 + 标签）
 */
@Data
public class ArchiveArticleItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime publishTime;

    private List<Tag> tags;
}
