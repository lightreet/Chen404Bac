package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReaderProgressVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long bookId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterId;
    private Integer blockIndex;
    private Integer characterOffset;
    private BigDecimal progressPercent;
    private String locatorContext;
    private Integer contentVersion;
    private Boolean finished;
    private LocalDateTime lastReadAt;
}
