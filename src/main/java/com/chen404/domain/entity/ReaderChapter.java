package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("reader_chapter")
public class ReaderChapter implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Integer chapterOrder;
    private String title;
    private String volumeTitle;
    private String sourceHref;
    private String contentHtml;
    private String contentText;
    private Integer charCount;
    private String contentHash;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
