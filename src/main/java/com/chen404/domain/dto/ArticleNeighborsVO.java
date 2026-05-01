package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "文章相邻导航结果")
@Data
public class ArticleNeighborsVO {

    @Schema(description = "上一篇文章摘要")
    private ArticleNeighborVO prev;

    @Schema(description = "下一篇文章摘要")
    private ArticleNeighborVO next;
}
