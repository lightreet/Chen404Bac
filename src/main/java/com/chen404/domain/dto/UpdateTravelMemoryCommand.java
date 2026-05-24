package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新旅行纪念地点命令对象。
 */
@Schema(description = "更新旅行纪念地点命令对象")
@Data
public class UpdateTravelMemoryCommand {

    @Schema(description = "地点标题", example = "成都春日散记")
    @NotBlank(message = "地点标题不能为空")
    @Size(max = 120, message = "地点标题长度不能超过 120 个字符")
    private String title;

    @Schema(description = "省份", example = "四川")
    @Size(max = 64, message = "省份长度不能超过 64 个字符")
    private String province;

    @Schema(description = "城市", example = "成都")
    @Size(max = 64, message = "城市长度不能超过 64 个字符")
    private String city;

    @Schema(description = "展示纬度", example = "30.572815")
    private BigDecimal latitude;

    @Schema(description = "展示经度", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "地点简介", example = "把同一座城市里几段不同的散步记在一起。")
    @Size(max = 1000, message = "地点简介长度不能超过 1000 个字符")
    private String summaryNote;

    @Schema(description = "到访开始时间")
    private LocalDateTime visitedAt;

    @Schema(description = "到访结束时间")
    private LocalDateTime visitedEndAt;

    @Schema(description = "展示状态：0-隐藏 1-展示", example = "1")
    private Integer status;

    @Schema(description = "排序值", example = "10")
    private Integer sortOrder;

    @Schema(description = "地点下的照片列表")
    @Valid
    @NotEmpty(message = "至少需要保留一张照片")
    private List<TravelMemoryEntryUpsertCommand> entries;
}
