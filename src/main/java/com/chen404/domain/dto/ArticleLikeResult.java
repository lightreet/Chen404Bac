package com.chen404.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLikeResult {
    private Integer likes;
    /** 登录用户切换点赞后的状态；匿名无此字段 */
    private Boolean liked;
}
