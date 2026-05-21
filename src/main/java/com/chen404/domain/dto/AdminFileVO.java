package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminFileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String fileName;

    private String fileOriginalName;

    private String fileUrl;

    private String objectName;

    private Long fileSize;

    private String contentType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String status;

    private String refType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long refId;

    private String referenceStatus;

    private Integer referenceCount;

    private List<String> referenceModules;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
