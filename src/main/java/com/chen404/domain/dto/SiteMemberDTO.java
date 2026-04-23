package com.chen404.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteMemberDTO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer trustLevel;
    private LocalDateTime createTime;
}
