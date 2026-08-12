package com.chen404.domain.dto;

import lombok.Data;

/**
 * 管理后台运行时功能开关。
 *
 * <p>所有字段支持局部更新；读取接口始终返回补齐默认值后的完整配置。</p>
 */
@Data
public class FeatureToggleConfigDTO {

    private Boolean articleCreationEnabled;
    private Boolean travelCreationEnabled;
    private Boolean musicCreationEnabled;
    private Boolean adminNotificationEnabled;
    private Boolean aiArticleAssistEnabled;
    private Boolean aiMusicAssistEnabled;
    private Boolean aiArticleRecommendEnabled;
}
