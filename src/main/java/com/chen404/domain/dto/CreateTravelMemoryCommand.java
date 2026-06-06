package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建旅行记忆地点命令对象。
 */
@Schema(description = "创建旅行记忆地点命令对象")
@Data
public class CreateTravelMemoryCommand {

    @Schema(description = "地点标题", example = "广州的又一次认识")
    @NotBlank(message = "地点标题不能为空")
    @Size(max = 120, message = "地点标题长度不能超过 120 个字符")
    private String title;

    @Schema(description = "省份", example = "广东省")
    @Size(max = 64, message = "省份长度不能超过 64 个字符")
    private String province;

    @Schema(description = "城市", example = "广州市")
    @Size(max = 64, message = "城市长度不能超过 64 个字符")
    private String city;

    @Schema(description = "展示纬度", example = "23.129110")
    private BigDecimal latitude;

    @Schema(description = "展示经度", example = "113.264385")
    private BigDecimal longitude;

    @Schema(description = "整体摘要", example = "把同一趟旅行中的多个景点和路上片段整理成一篇记忆。")
    @Size(max = 1000, message = "整体摘要长度不能超过 1000 个字符")
    private String summaryNote;

    @Schema(description = "旅行开始日期")
    private LocalDateTime visitedAt;

    @Schema(description = "旅行结束日期")
    private LocalDateTime visitedEndAt;

    @Schema(description = "显示状态：0-隐藏 1-显示", example = "1")
    private Integer status;

    @Schema(description = "排序值", example = "10")
    private Integer sortOrder;

    @Schema(description = "旅途片段列表，优先使用该结构保存")
    @Valid
    private List<TravelMemoryStopUpsertCommand> stops;

    @Schema(description = "地点下的照片列表，兼容旧版扁平编辑保存")
    @Valid
    private List<TravelMemoryEntryUpsertCommand> entries;
}
