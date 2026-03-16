package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统文件实体
 * 用于跟踪和管理上传的文件资源
 */
@Data
@TableName("sys_file")
public class SysFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名称（存储文件名，与 objectName 一致或展示用）
     */
    private String fileName;

    /**
     * 原文件名（与 chen404.sql 旧表 file_original_name 兼容）
     */
    @TableField("file_original_name")
    private String fileOriginalName;

    /**
     * 对象名称（存储路径）
     */
    private String objectName;

    /**
     * 文件存储路径（与 chen404.sql 旧表 file_path 兼容，通常与 objectName 一致）
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String contentType;

    /**
     * 上传用户ID
     */
    private Long userId;

    /**
     * 文件状态：TEMP-临时，PERMANENT-永久，DELETED-已删除
     */
    private String status;

    /**
     * 引用类型：ARTICLE-文章，AVATAR-头像，COVER-封面等
     */
    private String refType;

    /**
     * 引用ID（如文章ID）
     */
    private Long refId;

    /**
     * 过期时间（临时文件自动清理时间）
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 文件状态枚举
     */
    public interface Status {
        String TEMP = "TEMP";           // 临时文件
        String PERMANENT = "PERMANENT"; // 永久文件
        String DELETED = "DELETED";     // 已删除
    }

    /**
     * 引用类型枚举
     */
    public interface RefType {
        String ARTICLE_CONTENT = "ARTICLE_CONTENT"; // 文章内容图片
        String ARTICLE_COVER = "ARTICLE_COVER";     // 文章封面
        String AVATAR = "AVATAR";                   // 用户头像
        String OTHER = "OTHER";                     // 其他
    }
}
