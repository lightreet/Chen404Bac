package com.chen404.service;

import com.chen404.domain.dto.FeatureToggleConfigDTO;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.RuntimeFeatureEnum;

import java.util.List;

/**
 * 数据库驱动的运行时业务功能开关服务。
 */
public interface FeatureToggleService {

    /**
     * 获取管理后台可编辑的完整功能开关。
     *
     * @return 补齐默认值后的功能开关
     */
    FeatureToggleConfigDTO getAdminConfig();

    /**
     * 局部更新运行时功能开关，并让当前服务实例立即生效。
     *
     * @param patch 本次更新的字段
     * @param operatorId 管理员用户 ID
     * @return 更新后的完整功能开关
     */
    FeatureToggleConfigDTO updateAdminConfig(FeatureToggleConfigDTO patch, Long operatorId);

    /**
     * 判断指定运行时业务功能是否启用。
     *
     * @param feature 功能枚举
     * @return 是否启用
     */
    boolean isEnabled(RuntimeFeatureEnum feature);

    /**
     * 根据用户身份和运行时开关解析最终可用能力；管理员不受创作灰度开关影响。
     *
     * @param user 已补齐角色和信任级别的用户
     * @return 当前可用能力编码
     */
    List<String> resolveAvailableCapabilities(User user);
}
