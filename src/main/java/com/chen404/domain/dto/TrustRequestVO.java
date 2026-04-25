package com.chen404.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrustRequestVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String userEmail;
    private Integer userTrustLevel;
    private Integer status;
    private String reason;
    private List<TrustRequestAttachmentVO> attachments;
    private String contactEmail;
    private String reviewNote;
    private Long reviewedBy;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
