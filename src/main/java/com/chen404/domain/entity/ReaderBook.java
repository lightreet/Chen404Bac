package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("reader_book")
public class ReaderBook implements Serializable {

    public static final String STATUS_READY = "ready";
    public static final String STATUS_FAILED = "failed";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String title;
    private String author;
    private String description;
    private String language;
    private String visibility;
    private String sourceFormat;
    private String sourceEncoding;
    private Long sourceFileId;
    private String sourceFileUrl;
    private String contentChecksum;
    private String status;
    private String parseMessage;
    private Integer chapterCount;
    private Long totalCharCount;
    private Long coverAssetId;
    private Long coverFileId;
    private Integer contentVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
