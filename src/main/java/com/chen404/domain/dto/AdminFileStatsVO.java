package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "后台文件统计概览")
@Data
public class AdminFileStatsVO {

    @Schema(description = "文件总数", example = "136")
    private Long totalFiles;

    @Schema(description = "文件总大小，单位字节", example = "52428800")
    private Long totalSize;

    @Schema(description = "已引用文件数", example = "72")
    private Long referencedCount;

    @Schema(description = "待绑定文件数", example = "15")
    private Long pendingCount;

    @Schema(description = "未引用文件数", example = "41")
    private Long unreferencedCount;

    @Schema(description = "已删除文件数", example = "8")
    private Long deletedCount;

    @Schema(description = "引用记录总数", example = "96")
    private Long referenceRecordCount;

    @Schema(description = "按引用状态统计")
    private List<AdminFileStatsBucketVO> statusBuckets;

    @Schema(description = "按上传归属类型统计")
    private List<AdminFileStatsBucketVO> refTypeBuckets;
}
