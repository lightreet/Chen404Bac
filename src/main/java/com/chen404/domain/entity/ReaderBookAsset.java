package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("reader_book_asset")
public class ReaderBookAsset implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private String sourcePath;
    private String sourcePathHash;
    private String fileName;
    private String mediaType;
    private Long fileSize;
    private String contentHash;
    private byte[] assetData;
    private Boolean isCover;
    private LocalDateTime createTime;
}
