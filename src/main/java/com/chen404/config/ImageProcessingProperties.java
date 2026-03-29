package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上传图片压缩为 WebP 的可调参数
 */
@Data
@ConfigurationProperties(prefix = "app.image-processing")
public class ImageProcessingProperties {

    /**
     * 是否启用压缩（关闭则行为与改造前一致）
     */
    private boolean enabled = true;

    /**
     * WebP 有损质量，约 1–100，数值越大体积通常越大
     */
    private int webpQuality = 82;

    /**
     * 含透明通道时是否使用 WebP 无损（体积更大）
     */
    private boolean losslessWebpForAlpha = false;

    /**
     * 正文图最长边像素上限
     */
    private int maxEdgeArticleContent = 1920;

    /**
     * 封面最长边像素上限
     */
    private int maxEdgeArticleCover = 1600;

    /**
     * 头像输出正方形边长
     */
    private int avatarSize = 512;
}
