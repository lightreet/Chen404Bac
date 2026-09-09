package com.chen404.service;

import com.chen404.domain.PageResult;
import com.chen404.domain.dto.CreateTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestVO;

public interface UserTrustRequestService {

    TrustRequestVO createRequest(Long userId, CreateTrustRequestDTO dto);

    TrustRequestVO getLatestForUser(Long userId);

    PageResult<TrustRequestVO> getAdminRequests(Integer page, Integer size, Integer status, String keyword);

    TrustRequestVO approveRequest(Long requestId, Long adminId, String reviewNote);

    TrustRequestVO rejectRequest(Long requestId, Long adminId, String reviewNote);

    /**
     * 管理员登录后使用邮件中的一次性令牌确认审批。
     */
    TrustRequestVO approveByEmailToken(String token, Long adminId);
}
