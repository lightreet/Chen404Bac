package com.chen404.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阅读笔记视图；不暴露记录用户 ID，避免私有笔记身份信息外泄。
 */
@Data
public class ReaderNoteVO {

    private Long id;
    private Long bookId;
    private Long chapterId;
    private Long targetChapterId;
    private Integer chapterOrder;
    private String chapterTitle;
    private Integer startBlockIndex;
    private Integer startCharacterOffset;
    private Integer endBlockIndex;
    private Integer endCharacterOffset;
    private String excerpt;
    private String reflection;
    private String highlightColor;
    private String prefixContext;
    private String suffixContext;
    private Integer contentVersion;
    private Boolean contentChanged;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
