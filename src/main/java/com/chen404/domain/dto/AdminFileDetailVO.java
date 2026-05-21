package com.chen404.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminFileDetailVO extends AdminFileVO {

    private List<AdminFileReferenceVO> references;
}
