package com.chen404.domain.dto;

import lombok.Data;

/**
 * 站点公开作者资料，仅用于前台展示。
 */
@Data
public class SiteOwnerDTO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
}
