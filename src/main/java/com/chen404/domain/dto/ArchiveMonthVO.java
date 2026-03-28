package com.chen404.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ArchiveMonthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int month;
    private int count;
    private List<ArchiveArticleItem> articles;
}
