package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 站点公开作者资料，仅用于前台展示。
 */
@Data
public class SiteOwnerDTO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String bio;
    private String memberLabel;
}
