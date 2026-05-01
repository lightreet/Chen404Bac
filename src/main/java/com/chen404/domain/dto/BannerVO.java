package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图视图对象。
 */
@Schema(description = "首页轮播图视图对象")
@Data
public class BannerVO {

    @Schema(description = "轮播图ID", example = "1")
    private Long id;

    @Schema(description = "标题", example = "欢迎来到 Chen404")
    private String title;

    @Schema(description = "副标题", example = "记录技术与生活")
    private String subtitle;

    @Schema(description = "图片地址", example = "https://cdn.example.com/banner.jpg")
    private String image;

    @Schema(description = "跳转链接", example = "https://github.com")
    private String link;

    @Schema(description = "打开方式：0-当前页 1-新窗口", example = "1")
    private Integer target;

    @Schema(description = "展示位置", example = "1")
    private Integer position;

    @Schema(description = "背景色", example = "#ffffff")
    private String backgroundColor;

    @Schema(description = "文字颜色", example = "#222222")
    private String textColor;

    @Schema(description = "排序值", example = "10")
    private Integer sortOrder;

    @Schema(description = "状态：0-停用 1-启用", example = "1")
    private Integer status;

    @Schema(description = "开始生效时间")
    private LocalDateTime startTime;

    @Schema(description = "结束生效时间")
    private LocalDateTime endTime;
}
