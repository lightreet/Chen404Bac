package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行记忆地点详情视图对象。
 */
@Schema(description = "旅行记忆地点详情视图对象")
@Data
public class TravelMemoryLocationDetailVO {

    @Schema(description = "地点 ID", example = "1001")
    private Long id;

    @Schema(description = "地点标题", example = "广州的又一次认识")
    private String title;

    @Schema(description = "省份", example = "广东省")
    private String province;

    @Schema(description = "城市", example = "广州市")
    private String city;

    @Schema(description = "展示纬度", example = "23.129110")
    private BigDecimal latitude;

    @Schema(description = "展示经度", example = "113.264385")
    private BigDecimal longitude;

    @Schema(description = "整体摘要", example = "把同一趟旅行中的多个景点和路上片段整理成一篇记忆。")
    private String summaryNote;

    @Schema(description = "封面图地址", example = "https://cdn.example.com/travel/gz-cover.jpg")
    private String coverImage;

    @Schema(description = "旅行开始日期")
    private LocalDateTime visitedAt;

    @Schema(description = "旅行结束日期")
    private LocalDateTime visitedEndAt;

    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer status;

    @Schema(description = "可见性：0-公开 2-知友可见", example = "2")
    private Integer visibility;

    @Schema(description = "排序值", example = "10")
    private Integer sortOrder;

    @Schema(description = "照片数量", example = "7")
    private Integer entryCount;

    @Schema(description = "扁平照片列表，兼容旧版读取")
    private List<TravelMemoryEntryVO> entries;

    @Schema(description = "旅途片段列表")
    private List<TravelMemoryStopVO> stops;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "地点创建者摘要")
    private ArticleAuthorVO creator;

    @Schema(description = "当前用户是否可编辑")
    private Boolean canEdit;

    @Schema(description = "当前用户是否可删除")
    private Boolean canDelete;
}
