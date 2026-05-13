package com.chen404.domain.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站点配置：当前包含基础站点信息与各页面 Hero 封面图。
 */
@Data
public class SiteConfigDTO {

    private String siteName;
    private String siteDescription;
    private String siteLogo;
    private String siteFavicon;
    private String icp;
    private String beian;
    private String github;
    private String email;
    private String copyright;
    private String seoKeywords;
    private String seoDescription;
    private Boolean commentAudit;
    private Boolean commentGuest;
    private Map<String, String> heroImages = new LinkedHashMap<>();
    private Map<String, String> heroImagePositions = new LinkedHashMap<>();
}
