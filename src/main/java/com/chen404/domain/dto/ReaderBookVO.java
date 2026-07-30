package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReaderBookVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String author;
    private String description;
    private String language;
    private String visibility;
    private Boolean ownedByCurrentUser;
    private String sourceFormat;
    private String sourceEncoding;
    private String status;
    private String parseMessage;
    private Integer chapterCount;
    private Long totalCharCount;
    private Integer contentVersion;
    private String coverUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentChapterId;
    private String currentChapterTitle;
    private BigDecimal progressPercent;
    private Boolean finished;
    private LocalDateTime lastReadAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
