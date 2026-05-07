package com.chen404.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.site-runtime")
public class SiteRuntimeProperties {

    /**
     * 首页、分类详情、标签详情等文章列表默认分页大小。
     */
    private int articlePageSize = 10;

    /**
     * 图片上传大小限制，单位字节。
     */
    private long uploadMaxSize = 12L * 1024 * 1024;

    /**
     * 允许上传的图片后缀。
     */
    private List<String> uploadAllowTypes = new ArrayList<>(List.of("jpg", "jpeg", "png", "gif", "webp", "bmp"));
}
