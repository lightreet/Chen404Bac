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
@TableName("sys_file")
public class SysFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    @TableField("file_original_name")
    private String fileOriginalName;

    private String objectName;

    private String storageScope;

    private String bucketName;

    @TableField("file_path")
    private String filePath;

    private String fileUrl;

    private Long fileSize;

    private String contentType;

    private Long userId;

    private String status;

    private String refType;

    private Long refId;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public interface Status {
        String TEMP = "TEMP";
        String PERMANENT = "PERMANENT";
        String DELETED = "DELETED";
    }

    public interface StorageScope {
        String PUBLIC = "PUBLIC";
        String PROTECTED = "PROTECTED";
    }

    public interface RefType {
        String ARTICLE_CONTENT = "ARTICLE_CONTENT";
        String ARTICLE_COVER = "ARTICLE_COVER";
        String SITE_ASSET = "SITE_ASSET";
        String SITE_HERO = "SITE_HERO";
        String AVATAR = "AVATAR";
        String TRUST_REQUEST_ATTACHMENT = "TRUST_REQUEST_ATTACHMENT";
        String TRAVEL_MEMORY_IMAGE = "TRAVEL_MEMORY_IMAGE";
        String MUSIC_AUDIO = "MUSIC_AUDIO";
        String MUSIC_COVER = "MUSIC_COVER";
        String NOVEL_SOURCE = "NOVEL_SOURCE";
        String NOVEL_COVER = "NOVEL_COVER";
        String OTHER = "OTHER";
    }
}
