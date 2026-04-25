package com.chen404.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrustRequestAttachmentVO {

    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private LocalDateTime createTime;
}
