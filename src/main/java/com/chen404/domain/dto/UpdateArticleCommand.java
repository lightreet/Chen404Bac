package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 编辑命令复用写入字段，额外携带读取时的版本以检测并发冲突。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新文章命令对象")
public class UpdateArticleCommand extends ArticleWriteCommand {
    @Schema(description = "读取文章时返回的编辑版本；旧客户端未传时检测本次请求期间的并发修改")
    @PositiveOrZero(message = "文章版本不能为负数")
    private Integer version;
}
