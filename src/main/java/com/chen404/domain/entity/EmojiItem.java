package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("emoji_item")
public class EmojiItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packCode;

    private String shortcode;

    private String label;

    private String category;

    /**
     * 类型：0-unicode 1-image
     */
    private Integer type;

    private String unicode;

    private String assetUrl;

    private Integer width;

    private Integer height;

    private Integer enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public interface Type {
        int UNICODE = 0;
        int IMAGE = 1;
    }
}

