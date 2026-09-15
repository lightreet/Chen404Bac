package com.chen404.controller;

import com.chen404.domain.dto.RemoteImageImportDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ArticleImageImportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArticleImageImportControllerTest {
    @Test
    void requiresAuthenticatedIdentityAndUsesItsUserId() {
        ArticleImageImportService service = mock(ArticleImageImportService.class);
        var controller = new ArticleImageImportController(service);
        RemoteImageImportDTO request = new RemoteImageImportDTO();
        request.setUrl("https://example.com/image");
        assertThrows(UnauthorizedException.class, () -> controller.importImage(request, null));
        verifyNoInteractions(service);
        UploadFileVO uploaded = new UploadFileVO();
        uploaded.setUrl("/api/files/900");
        when(service.importImage(request.getUrl(), 7L)).thenReturn(uploaded);
        assertEquals(uploaded, controller.importImage(request, new AuthenticatedUser(7L, "author", "USER")).getData());
    }
}
