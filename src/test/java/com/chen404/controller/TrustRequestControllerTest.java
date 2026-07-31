package com.chen404.controller;

import com.chen404.domain.dto.EmailApproveTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.UserTrustRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrustRequestControllerTest {

    @Test
    void getEmailApprovalShouldOnlyRedirectWithoutChangingState() {
        UserTrustRequestService service = mock(UserTrustRequestService.class);
        TrustRequestController controller = new TrustRequestController(service, "https://www.chen404.cn");

        ResponseEntity<Void> response = controller.openEmailApproval("one-time-token");

        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertEquals(
                "https://www.chen404.cn/admin?tab=trust-requests#emailApproveToken=one-time-token",
                response.getHeaders().getLocation().toString()
        );
        verifyNoInteractions(service);
    }

    @Test
    void postEmailApprovalShouldRecordAuthenticatedAdmin() {
        UserTrustRequestService service = mock(UserTrustRequestService.class);
        TrustRequestController controller = new TrustRequestController(service, "https://www.chen404.cn");
        EmailApproveTrustRequestDTO dto = new EmailApproveTrustRequestDTO();
        dto.setToken("one-time-token");
        TrustRequestVO approved = new TrustRequestVO();
        when(service.approveByEmailToken("one-time-token", 7L)).thenReturn(approved);

        controller.approveByEmail(dto, new AuthenticatedUser(7L, "admin", "admin"));

        verify(service).approveByEmailToken("one-time-token", 7L);
    }
}
