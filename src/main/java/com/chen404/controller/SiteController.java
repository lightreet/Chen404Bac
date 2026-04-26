package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.SiteConfigDTO;
import com.chen404.domain.dto.SiteMemberDTO;
import com.chen404.domain.dto.SiteOwnerDTO;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.User;
import com.chen404.service.BannerService;
import com.chen404.service.SiteConfigService;
import com.chen404.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站点控制器
 */
@RestController
@RequestMapping("/site")
public class SiteController {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private UserService userService;

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
    public Result<SiteConfigDTO> getSiteConfig() {
        return Result.success(toPublicConfig(siteConfigService.getConfig()));
    }

    /**
     * 获取站点管理员公开资料。
     */
    @GetMapping("/owner")
    public Result<SiteOwnerDTO> getSiteOwner() {
        return Result.success(toPublicOwner(userService.getCurrentUser(1L)));
    }

    @GetMapping("/members")
    public Result<List<SiteMemberDTO>> getSiteMembers() {
        return Result.success(userService.listPublicUsers().stream()
                .map(SiteController::toPublicMember)
                .toList());
    }

    /**
     * 更新站点配置（管理员）
     */
    @RequireAdmin
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
        config.setGithub(source.getGithub());
        config.setEmail(source.getEmail());
        config.setHeroImages(source.getHeroImages());
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
        member.setAvatar(source.getAvatar());
        member.setBio(source.getBio());
        member.setTrustLevel(source.getTrustLevel());
        member.setMemberLabel(source.getMemberLabel());
        member.setCreateTime(source.getCreateTime());
        return member;
    }
}
