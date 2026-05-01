package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "文章收藏切换结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteToggleResultDTO {

    @Schema(description = "当前是否已收藏", example = "true")
    private Boolean favorited;
}
