package com.chen404.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 将上传图片解码、缩放后编码为 WebP；动图 GIF 不处理（返回 empty 走原文件上传），
 * 以便文章/页面封面可正式支持 GIF 动图。
 */
public interface ImageProcessingService {

    /**
     * @return 有值表示已压缩为 WebP；empty 表示使用原始 {@link MultipartFile} 上传
     */
    Optional<ProcessedImage> process(MultipartFile file, String refType);
}
