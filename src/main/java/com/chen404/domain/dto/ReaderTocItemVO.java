package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReaderTocItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String label;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterId;
    private String fragment;
    private Integer depth;
    private List<ReaderTocItemVO> children = new ArrayList<>();
}
