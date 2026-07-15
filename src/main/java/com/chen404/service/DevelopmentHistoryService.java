package com.chen404.service;

import com.chen404.domain.dto.DevelopmentHistoryVO;

/**
 * 站点开发历程服务。
 */
public interface DevelopmentHistoryService {

    /**
     * 聚合配置仓库的 GitHub 提交并返回缓存结果。
     *
     * @return 开发历程数据；上游不可用时可能返回带提示的空结果
     */
    DevelopmentHistoryVO getDevelopmentHistory();
}
