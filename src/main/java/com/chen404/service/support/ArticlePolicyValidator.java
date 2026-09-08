package com.chen404.service.support;

import com.chen404.domain.entity.Article;
import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.exception.BadRequestException;

import java.util.Arrays;

/** 文章写入业务边界校验，覆盖绕过 Controller Bean Validation 的内部调用。 */
public final class ArticlePolicyValidator {
    private ArticlePolicyValidator() {
    }

    public static void validate(Article article) {
        if (article == null || Arrays.stream(ArticleStatusEnum.values())
                .noneMatch(status -> ArticleStatusEnum.is(article.getStatus(), status))) {
            throw new BadRequestException("文章状态无效");
        }
        if (article.getVisibility() != null && Arrays.stream(ArticleVisibilityEnum.values())
                .noneMatch(value -> value.getValue() == article.getVisibility())) {
            throw new BadRequestException("文章可见性无效");
        }
        if (article.getCommentPolicy() != null && Arrays.stream(ArticleCommentPolicyEnum.values())
                .noneMatch(value -> value.getValue() == article.getCommentPolicy())) {
            throw new BadRequestException("文章评论策略无效");
        }
        validateFlag(article.getIsTop());
        validateFlag(article.getIsRecommend());
        validateFlag(article.getIsOriginal());
    }

    /** 可选标志保留缺省语义，显式值只允许 0/1。 */
    private static void validateFlag(Integer flag) {
        if (flag != null && flag != 0 && flag != 1) {
            throw new BadRequestException("文章标志只允许 0 或 1");
        }
    }
}
