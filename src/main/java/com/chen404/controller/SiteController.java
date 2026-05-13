package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.BannerVO;
import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.domain.dto.SiteMemberDTO;
import com.chen404.domain.dto.SiteOwnerDTO;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.User;
import com.chen404.service.BannerService;
import com.chen404.service.SiteConfigService;
import com.chen404.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站点控制器
 */
@Tag(name = "站点", description = "站点配置、轮播图与公开资料接口")
@RestController
@RequestMapping("/site")
public class SiteController {

    private final BannerService bannerService;
    private final SiteConfigService siteConfigService;
    private final UserService userService;
    private final HomeViewConverter homeViewConverter;

    public SiteController(
            BannerService bannerService,
            SiteConfigService siteConfigService,
            UserService userService,
            HomeViewConverter homeViewConverter) {
        this.bannerService = bannerService;
        this.siteConfigService = siteConfigService;
        this.userService = userService;
        this.homeViewConverter = homeViewConverter;
    }

    /**
     * 获取轮播图列表
     */
    @Operation(summary = "获取轮播图列表", description = "按位置获取启用中的轮播图")
    @GetMapping("/banners")
    public Result<List<BannerVO>> getBanners(
            @Parameter(description = "轮播图位置，默认 1", required = true) @RequestParam(defaultValue = "1") Integer position) {
        List<Banner> banners = bannerService.getBannersByPosition(position);
        return Result.success(homeViewConverter.toBannerVOList(banners));
    }

    /**
     * 获取站点配置
     */
    @Operation(summary = "获取站点配置", description = "返回站点名称、描述、Logo、SEO 等公开配置")
    @GetMapping("/config")
    public Result<SiteConfigDTO> getSiteConfig() {
        return Result.success(toPublicConfig(siteConfigService.getConfig()));
    }

    /**
     * 获取站点管理员公开资料。
     */
    @Operation(summary = "获取站长公开资料", description = "返回站长的公开展示信息")
    @GetMapping("/owner")
    public Result<SiteOwnerDTO> getSiteOwner() {
        return Result.success(toPublicOwner(userService.getCurrentUser(1L)));
    }

    @Operation(summary = "获取站点成员列表", description = "返回可公开展示的站点成员列表")
    @GetMapping("/members")
    public Result<List<SiteMemberDTO>> getSiteMembers() {
        return Result.success(userService.listPublicUsers().stream()
                .map(SiteController::toPublicMember)
                .toList());
    }

    @Operation(summary = "获取站点成员详情", description = "按用户 ID 获取单个成员的公开资料")
    @GetMapping("/users/{id}")
    public Result<SiteMemberDTO> getSiteUser(
            @Parameter(description = "用户 ID", required = true) @PathVariable Long id) {
        User user = userService.getPublicUser(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(toPublicMember(user));
    }

    /**
     * 更新站点配置（管理员）
     */
    @RequireAdmin
    @Operation(summary = "更新站点配置", description = "仅管理员可更新站点公开配置")
    @PutMapping("/config")
    public Result<SiteConfigDTO> updateSiteConfig(@RequestBody SiteConfigDTO request) {
        return Result.success("保存成功", siteConfigService.updateConfig(request));
    }

    private static SiteConfigDTO toPublicConfig(SiteConfigDTO source) {
        SiteConfigDTO config = new SiteConfigDTO();
        config.setSiteName(source.getSiteName());
        config.setSiteDescription(source.getSiteDescription());
        config.setSiteLogo(source.getSiteLogo());
        config.setSiteFavicon(source.getSiteFavicon());
        config.setIcp(source.getIcp());
        config.setBeian(source.getBeian());
        config.setGithub(source.getGithub());
        config.setEmail(source.getEmail());
        config.setCopyright(source.getCopyright());
        config.setSeoKeywords(source.getSeoKeywords());
        config.setSeoDescription(source.getSeoDescription());
        config.setCommentAudit(source.getCommentAudit());
        config.setCommentGuest(source.getCommentGuest());
        config.setHeroImages(source.getHeroImages());
        config.setHeroImagePositions(source.getHeroImagePositions());
        return config;
    }

    private static SiteOwnerDTO toPublicOwner(User source) {
        if (source == null) {
            return null;
        }
        SiteOwnerDTO owner = new SiteOwnerDTO();
        owner.setId(source.getId());
        owner.setUsername(source.getUsername());
        owner.setNickname(source.getNickname());
        owner.setEmail(source.getEmail());
        owner.setAvatar(source.getAvatar());
        owner.setBio(source.getBio());
        owner.setMemberLabel(source.getMemberLabel());
        return owner;
    }

    private static SiteMemberDTO toPublicMember(User source) {
        if (source == null) {
            return null;
        }
        SiteMemberDTO member = new SiteMemberDTO();
        member.setId(source.getId());
        member.setUsername(source.getUsername());
        member.setNickname(source.getNickname());
        member.setEmail(source.getEmail());
        member.setAvatar(source.getAvatar());
        member.setBio(source.getBio());
        member.setTrustLevel(source.getTrustLevel());
        member.setMemberLabel(source.getMemberLabel());
        member.setCreateTime(source.getCreateTime());
        return member;
    }
}
