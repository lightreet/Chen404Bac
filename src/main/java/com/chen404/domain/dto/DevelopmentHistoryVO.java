package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 站点开发历程聚合数据。
 */
@Schema(description = "站点开发历程")
@Data
public class DevelopmentHistoryVO {

    @Schema(description = "提交记录")
    private List<DevelopmentCommitVO> commits = new ArrayList<>();

    @Schema(description = "仓库摘要")
    private List<DevelopmentRepositoryVO> repositories = new ArrayList<>();

    @Schema(description = "提交总数")
    private int totalCommits;

    @Schema(description = "贡献者数量")
    private int contributorCount;

    @Schema(description = "数据生成时间")
    private Instant generatedAt;

    @Schema(description = "数据是否可用")
    private boolean available;

    @Schema(description = "是否使用过期缓存")
    private boolean stale;

    @Schema(description = "同步提示，完整成功时为空")
    private String notice;
}
