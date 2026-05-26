package com.chen404.service;

import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminFileDetailVO;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.domain.dto.AdminFileVO;

public interface AdminFileService {

    PageResult<AdminFileVO> getAdminFiles(
            Integer page,
            Integer size,
            String keyword,
            String status,
            String refType,
            Boolean referenced,
            String referenceStatus
    );

    AdminFileDetailVO getAdminFileDetail(Long fileId);

    AdminFileStatsVO getAdminFileStats();
}
