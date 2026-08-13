package com.chen404.service.impl;

import com.chen404.config.MinioConfig;
import com.chen404.service.FileStorageService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储服务实现
 */
@Slf4j
@Service
public class MinioStorageServiceImpl implements FileStorageService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Override
    public String uploadFile(MultipartFile file, String objectName) {
        return uploadFile(file, minioConfig.getBucketName(), objectName);
    }

    @Override
    public String uploadFile(MultipartFile file, String bucketName, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            return uploadFile(inputStream, bucketName, objectName, file.getContentType(), file.getSize());
        } catch (IOException e) {
            log.error("读取文件流失败", e);
            throw new IllegalStateException("读取文件流失败", e);
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectName, String contentType, long size) {
        return uploadFile(inputStream, minioConfig.getBucketName(), objectName, contentType, size);
    }

    @Override
    public String uploadFile(
            InputStream inputStream,
            String bucketName,
            String objectName,
            String contentType,
            long size) {
        try {
            ensureBucketExists(bucketName, minioConfig.getBucketName().equals(bucketName));

            // 上传文件
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );

            String fileUrl = minioConfig.getFileUrl(bucketName, objectName);
            log.info("文件上传成功: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("上传文件到MinIO失败", e);
            throw new IllegalStateException("上传文件失败", e);
        }
    }

    @Override
    public InputStream openFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception exception) {
            log.error("读取MinIO文件失败: bucket={}, object={}", bucketName, objectName, exception);
            throw new IllegalStateException("读取文件失败", exception);
        }
    }

    @Override
    public boolean deleteFile(String objectName) {
        return deleteFile(minioConfig.getBucketName(), objectName);
    }

    @Override
    public boolean deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}", objectName);
            return true;
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return false;
        }
    }

    @Override
    public String getFileUrl(String objectName) {
        return minioConfig.getFileUrl(objectName);
    }

    @Override
    public String getFileUrl(String bucketName, String objectName) {
        return minioConfig.getFileUrl(bucketName, objectName);
    }

    @Override
    public String getPresignedGetUrl(String bucketName, String objectName, int expiresMinutes) {
        try {
            ensureBucketExists(bucketName, minioConfig.getBucketName().equals(bucketName));
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(expiresMinutes, TimeUnit.MINUTES)
                            .build()
            );
            return minioConfig.externalizePresignedUrl(presignedUrl);
        } catch (Exception e) {
            log.error("获取文件下载链接失败: bucket={}, object={}", bucketName, objectName, e);
            throw new IllegalStateException("获取文件下载链接失败", e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        return exists(minioConfig.getBucketName(), objectName);
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取预签名上传URL（用于前端直传）
     *
     * @param objectName 对象名称
     * @param expires    过期时间（分钟）
     * @return 预签名URL
     */
    public String getPresignedUploadUrl(String objectName, int expires) {
        try {
            ensureBucketExists(minioConfig.getBucketName(), true);
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .method(Method.PUT)
                            .expiry(expires, TimeUnit.MINUTES)
                            .build()
            );
            return minioConfig.externalizePresignedUrl(presignedUrl);
        } catch (Exception e) {
            log.error("获取预签名URL失败", e);
            throw new IllegalStateException("获取上传链接失败", e);
        }
    }

    /**
     * 确保存储桶存在
     */
    private void ensureBucketExists(String bucketName, boolean publicReadable) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            log.info("创建存储桶: {}", bucketName);
        }

        if (!publicReadable) {
            minioClient.deleteBucketPolicy(
                    DeleteBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            return;
        }
        if (!exists) {
            String policy = "{\n" +
                    "    \"Version\": \"2012-10-17\",\n" +
                    "    \"Statement\": [\n" +
                    "        {\n" +
                    "            \"Effect\": \"Allow\",\n" +
                    "            \"Principal\": \"*\",\n" +
                    "            \"Action\": [\n" +
                    "                \"s3:GetObject\"\n" +
                    "            ],\n" +
                    "            \"Resource\": [\n" +
                    "                \"arn:aws:s3:::" + bucketName + "/*\"\n" +
                    "            ]\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}";
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );
        }
    }
}
