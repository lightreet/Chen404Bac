package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String eventType;

    private String title;

    private String summary;

    private AdminNotificationActorVO actor;

    private String resourceType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    private Boolean read;

    private LocalDateTime readTime;

    private LocalDateTime createTime;
}
