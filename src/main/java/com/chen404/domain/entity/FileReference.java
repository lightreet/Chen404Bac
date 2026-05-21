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
@TableName("file_reference")
public class FileReference implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private String moduleCode;

    private String bizType;

    private Long bizId;

    private String fieldKey;

    private String sourceType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public interface ModuleCode {
        String ARTICLE = "ARTICLE";
        String USER = "USER";
        String SITE_CONFIG = "SITE_CONFIG";
        String TRAVEL_MEMORY = "TRAVEL_MEMORY";
        String TRAVEL_MEMORY_ENTRY = "TRAVEL_MEMORY_ENTRY";
        String TRUST_REQUEST = "TRUST_REQUEST";
    }

    public interface BizType {
        String ARTICLE_CONTENT = "ARTICLE_CONTENT";
        String ARTICLE_COVER = "ARTICLE_COVER";
        String USER_AVATAR = "USER_AVATAR";
        String SITE_ASSET = "SITE_ASSET";
        String SITE_HERO = "SITE_HERO";
        String TRAVEL_MEMORY_COVER = "TRAVEL_MEMORY_COVER";
        String TRAVEL_MEMORY_ENTRY_IMAGE = "TRAVEL_MEMORY_ENTRY_IMAGE";
        String TRUST_REQUEST_ATTACHMENT = "TRUST_REQUEST_ATTACHMENT";
    }

    public interface FieldKey {
        String CONTENT = "content";
        String COVER_IMAGE = "coverImage";
        String AVATAR = "avatar";
        String IMAGE_URL = "imageUrl";
        String ATTACHMENTS = "attachments";
        String SITE_LOGO = "site.logo";
        String SITE_FAVICON = "site.favicon";
    }

    public interface SourceType {
        String DIRECT = "DIRECT";
        String URL_MATCH = "URL_MATCH";
        String LEGACY_REF = "LEGACY_REF";
    }
}
