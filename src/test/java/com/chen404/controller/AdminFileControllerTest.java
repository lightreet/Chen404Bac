package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.service.AdminFileService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminFileControllerTest {

    @Test
    void shouldReturnFullFileStatistics() {
        AdminFileService adminFileService = mock(AdminFileService.class);
        AdminFileController controller = new AdminFileController(adminFileService);
        AdminFileStatsVO stats = new AdminFileStatsVO();
        stats.setTotalFiles(136L);
        when(adminFileService.getAdminFileStats()).thenReturn(stats);

        Result<AdminFileStatsVO> result = controller.getAdminFileStats();

        assertSame(stats, result.getData());
        verify(adminFileService).getAdminFileStats();
    }

    @Test
    void shouldNotExposeHttpRebuildReferencesEntry() {
        assertFalse(
                Arrays.stream(AdminFileController.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("rebuildReferences")),
                "文件引用重建应仅通过 XXL-JOB 触发，不再暴露 HTTP 入口"
        );
    }
}
