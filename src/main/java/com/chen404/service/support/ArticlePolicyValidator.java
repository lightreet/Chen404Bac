package com.chen404.service.support;

import com.chen404.domain.ArticleConstraints;
import com.chen404.domain.dto.ArticleWriteCommand;
import com.chen404.domain.enums.ArticleCommentPolicyEnum;
import com.chen404.domain.enums.ArticleStatusEnum;
import com.chen404.domain.enums.ArticleVisibilityEnum;
import com.chen404.domain.enums.IntegerValueEnum;
import com.chen404.exception.BadRequestException;

/** 文章写入业务边界校验，覆盖绕过 Controller Bean Validation 的内部调用。 */
public final class ArticlePolicyValidator {
    private ArticlePolicyValidator() {
    }

    public static void validate(ArticleWriteCommand command) {
        if (command == null || !IntegerValueEnum.contains(ArticleStatusEnum.class, command.getStatus())) {
            throw new BadRequestException(ArticleConstraints.INVALID_STATUS);
        }
        if (command.getVisibility() != null && !IntegerValueEnum.contains(ArticleVisibilityEnum.class, command.getVisibility())) {
            throw new BadRequestException(ArticleConstraints.INVALID_VISIBILITY);
        }
        if (command.getCommentPolicy() != null && !IntegerValueEnum.contains(ArticleCommentPolicyEnum.class, command.getCommentPolicy())) {
            throw new BadRequestException(ArticleConstraints.INVALID_COMMENT_POLICY);
        }
        validateFlag(command.getIsTop());
        validateFlag(command.getIsRecommend());
        validateFlag(command.getIsOriginal());
    }

    /** 可选标志保留缺省语义，显式值只允许 0/1。 */
    private static void validateFlag(Integer flag) {
        if (flag != null && flag != 0 && flag != 1) {
            throw new BadRequestException("文章标志只允许 0 或 1");
        }
    }
}
