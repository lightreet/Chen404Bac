package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("reader_toc_item")
public class ReaderTocItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long parentId;
    private Long chapterId;
    private Integer itemOrder;
    private Integer depth;
    private String label;
    private String sourceHref;
    private String fragment;
    private LocalDateTime createTime;
}
