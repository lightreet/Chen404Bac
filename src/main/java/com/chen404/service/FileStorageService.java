package com.chen404.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file       文件
     * @param objectName 对象名称（路径）
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file, String objectName);

    String uploadFile(MultipartFile file, String bucketName, String objectName);

    /**
     * 上传文件
     *
     * @param inputStream 文件流
     * @param objectName  对象名称
     * @param contentType 内容类型
     * @param size        文件大小
     * @return 文件访问URL
     */
    String uploadFile(InputStream inputStream, String objectName, String contentType, long size);

    String uploadFile(
            InputStream inputStream,
            String bucketName,
            String objectName,
            String contentType,
            long size
    );

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     * @return 是否成功
     */
    boolean deleteFile(String objectName);

    boolean deleteFile(String bucketName, String objectName);

    /**
     * 获取文件访问URL
     *
     * @param objectName 对象名称
     * @return 访问URL
     */
    String getFileUrl(String objectName);

    String getFileUrl(String bucketName, String objectName);

    /**
     * 生成短时有效的下载地址，主要用于受保护存储桶。
     */
    String getPresignedGetUrl(String bucketName, String objectName, int expiresMinutes);

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    boolean exists(String objectName);

    boolean exists(String bucketName, String objectName);
}
