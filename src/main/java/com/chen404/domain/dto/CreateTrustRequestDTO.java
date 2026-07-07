package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 创建好友申请请求 DTO。
 */
@Schema(description = "创建好友申请请求参数")
@Data
public class CreateTrustRequestDTO {

    @Schema(description = "申请理由，请说明希望成为知友的原因", required = true, example = "希望查看知友可见内容")
    private String reason;

    @Schema(description = "附件 URL 列表，最多 3 个")
    private List<String> attachmentUrls;
}
