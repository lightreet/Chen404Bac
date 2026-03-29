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
 * 文章与 {@link SysFile} 的关联（正文图、封面等），便于管理与统计；正文仍存 URL。
 */
@Data
@TableName("article_file_ref")
public class ArticleFileRef implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long fileId;

    /**
     * {@link RefKind#CONTENT} / {@link RefKind#COVER}
     */
    private String refKind;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public interface RefKind {
        String CONTENT = "CONTENT";
        String COVER = "COVER";
    }
}
