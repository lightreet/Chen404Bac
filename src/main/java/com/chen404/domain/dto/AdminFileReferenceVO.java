package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AdminFileReferenceVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    private String moduleCode;

    private String bizType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    private String fieldKey;

    private String sourceType;

    private String bizLabel;
}
