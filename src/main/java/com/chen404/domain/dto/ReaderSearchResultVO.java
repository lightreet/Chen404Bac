package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderSearchResultVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterId;
    private String chapterTitle;
    private Integer chapterOrder;
    private String snippet;
}
