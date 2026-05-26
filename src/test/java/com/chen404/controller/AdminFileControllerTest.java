package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.service.AdminFileService;
import com.chen404.service.FileReferenceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminFileControllerTest {

    @Test
    void shouldReturnFullFileStatistics() {
        AdminFileService adminFileService = mock(AdminFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        AdminFileController controller = new AdminFileController(adminFileService, fileReferenceService);
        AdminFileStatsVO stats = new AdminFileStatsVO();
        stats.setTotalFiles(136L);
        when(adminFileService.getAdminFileStats()).thenReturn(stats);

        Result<AdminFileStatsVO> result = controller.getAdminFileStats();

        assertSame(stats, result.getData());
        verify(adminFileService).getAdminFileStats();
    }
}
