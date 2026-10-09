package com.chen404.controller;

import com.chen404.config.SiteRuntimeProperties;
import com.chen404.domain.dto.SingleFileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.exception.ForbiddenException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.TravelMemoryImageMetadataService;
import com.chen404.service.TravelVideoService;
import com.chen404.service.support.MotionPhotoFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadControllerTravelMotionPhotoTest {
    @Test
    void imageEndpointPreservesMotionAndAuthorizesBeforeProcessing() throws Exception {
        var files = mock(SysFileService.class);
        var metadata = mock(TravelMemoryImageMetadataService.class);
        var access = mock(AccessService.class);
        var videos = mock(TravelVideoService.class);
        var properties = new SiteRuntimeProperties();
        var controller = new UploadController(files, properties, metadata, access, videos, Runnable::run);
        var user = mock(AuthenticatedUser.class);
        when(user.getUserId()).thenReturn(7L);
        var form = new SingleFileUploadDTO();
        byte[] image = MotionPhotoFixtures.jpeg();
        form.setFile(new MockMultipartFile("file", "live.jpg", "image/jpeg",
                MotionPhotoFixtures.join(image, MotionPhotoFixtures.container())));
        properties.setUploadMaxSize(image.length);
        assertThrows(ForbiddenException.class, () -> controller.uploadTravelMemoryImage(form, user));
        verifyNoInteractions(videos, files);
        when(access.canCreateTravelMemory(7L)).thenReturn(true);
        var uploaded = new UploadFileVO();
        uploaded.setVideoUrl("/api/files/102");
        uploaded.setUrl("/api/files/101");
        when(videos.upload(any(), eq(7L), any())).thenReturn(uploaded);
        var result = controller.uploadTravelMemoryImage(form, user).getData();
        assertEquals("/api/files/102", result.getVideoUrl());
        assertEquals("/api/files/101", result.getUrl());
        verifyNoInteractions(files, metadata);
    }
}
