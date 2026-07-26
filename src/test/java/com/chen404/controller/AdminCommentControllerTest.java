package com.chen404.controller;

import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminCommentStatsVO;
import com.chen404.domain.dto.AdminCommentVO;
import com.chen404.domain.dto.ReviewCommentDTO;
import com.chen404.domain.enums.CommentSceneEnum;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AdminCommentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommentControllerTest {

    @Test
    void shouldDelegateAdminCommentQuery() {
        AdminCommentService service = mock(AdminCommentService.class);
        AdminCommentController controller = new AdminCommentController(service);
        PageResult<AdminCommentVO> pageResult = new PageResult<>(List.of(), 0L, 1L, 20L);
        when(service.getAdminComments(1, 20, 0, CommentSceneEnum.ALL, "chen"))
                .thenReturn(pageResult);

        Result<PageResult<AdminCommentVO>> result = controller.getAdminComments(
                1,
                20,
                0,
                CommentSceneEnum.ALL,
                "chen"
        );

        assertSame(pageResult, result.getData());
        verify(service).getAdminComments(1, 20, 0, CommentSceneEnum.ALL, "chen");
    }

    @Test
    void shouldReturnCommentStatistics() {
        AdminCommentService service = mock(AdminCommentService.class);
        AdminCommentController controller = new AdminCommentController(service);
        AdminCommentStatsVO stats = new AdminCommentStatsVO();
        stats.setPendingCount(3L);
        when(service.getAdminCommentStats()).thenReturn(stats);

        Result<AdminCommentStatsVO> result = controller.getAdminCommentStats();

        assertSame(stats, result.getData());
        verify(service).getAdminCommentStats();
    }

    @Test
    void shouldPassAdminIdentityWhenReviewing() {
        AdminCommentService service = mock(AdminCommentService.class);
        AdminCommentController controller = new AdminCommentController(service);
        ReviewCommentDTO request = new ReviewCommentDTO();
        request.setStatus(1);
        AuthenticatedUser admin = new AuthenticatedUser(99L, "admin", "ADMIN");
        AdminCommentVO reviewed = new AdminCommentVO();
        when(service.reviewComment(1001L, 1, 99L)).thenReturn(reviewed);

        Result<AdminCommentVO> result = controller.reviewComment(1001L, request, admin);

        assertSame(reviewed, result.getData());
        verify(service).reviewComment(1001L, 1, 99L);
    }
}
