package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ReaderChapterVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;
    private String bookTitle;
    private Integer chapterOrder;
    private Integer chapterCount;
    private String title;
    private String volumeTitle;
    private String contentHtml;
    private Integer charCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long previousChapterId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextChapterId;
}
