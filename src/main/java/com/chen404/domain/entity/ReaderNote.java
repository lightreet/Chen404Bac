package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户私有阅读笔记及其原文定位锚点。
 */
@Data
@TableName("reader_note")
public class ReaderNote implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bookId;
    private Long chapterId;
    private Integer chapterOrder;
    private String chapterTitle;
    private String chapterContentHash;
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
