package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrustRequestVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewedBy;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
