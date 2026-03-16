package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Banner;
import com.chen404.service.BannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点控制器
 */
@RestController
@RequestMapping("/site")
public class SiteController {

    @Autowired
    private BannerService bannerService;

    /**
     * 获取轮播图列表
     */
    @GetMapping("/banners")
    public Result<List<Banner>> getBanners(@RequestParam(defaultValue = "1") Integer position) {
        List<Banner> banners = bannerService.getBannersByPosition(position);
        return Result.success(banners);
    }

    /**
     * 获取站点配置
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getSiteConfig() {
        // 返回站点基础配置
        Map<String, Object> config = new HashMap<>();
        config.put("siteName", "Chen404 Blog");
        config.put("siteDescription", "一个热爱技术分享的博客");
        config.put("siteLogo", "/logo.svg");
        config.put("siteFavicon", "/favicon.ico");
        config.put("github", "https://github.com/chen404");
        config.put("email", "admin@chen404.com");
        return Result.success(config);
    }
}
