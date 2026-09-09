package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 创建文章命令；作者、计数和版本由业务服务设置。 */
@Schema(description = "创建文章命令对象")
public class CreateArticleCommand extends ArticleWriteCommand {
}
