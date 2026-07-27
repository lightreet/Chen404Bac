package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端评论审核统计。
 */
@Data
@Schema(description = "管理端评论审核统计")
public class AdminCommentStatsVO {

    @Schema(description = "评论总数")
    private Long totalCount;

    @Schema(description = "待审核数量")
    private Long pendingCount;

    @Schema(description = "已通过数量")
    private Long approvedCount;

    @Schema(description = "已拒绝数量")
    private Long rejectedCount;
}
