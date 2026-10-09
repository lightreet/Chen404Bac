package com.chen404.service;

import com.chen404.converter.TravelMemoryConverter;
import com.chen404.converter.TravelMobileUploadConverter;
import com.chen404.domain.dto.TravelMemoryEntryUpsertCommand;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.TravelVideoProcessor;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TravelVideoServiceTest {
    @Test
    void enforcesPermissionBeforeProcessingAndPreservesPosterContract() {
        AccessService access = mock(AccessService.class);
        SysFileService files = mock(SysFileService.class);
        TravelVideoProcessor processor = mock(TravelVideoProcessor.class);
        TravelVideoService service = new TravelVideoService(access, processor, files, Mappers.getMapper(TravelMobileUploadConverter.class));
        var upload = new MockMultipartFile("file", "phone.mov", "video/quicktime", new byte[] { 1 });
        assertThrows(ForbiddenException.class, () -> service.upload(upload, 7L));
        verifyNoInteractions(processor, files);
        when(access.canCreateTravelMemory(7L)).thenReturn(true);
        when(processor.process(upload)).thenReturn(new TravelVideoProcessor.ProcessedVideo(
                new TravelVideoProcessor.MediaFile("video.mp4", "video/mp4", new byte[] { 1 }),
                new TravelVideoProcessor.MediaFile("poster.jpg", "image/jpeg", new byte[] { 2 }), 2));
        when(files.uploadTempFile(any(), eq(7L), eq(SysFile.RefType.TRAVEL_MEMORY_VIDEO))).thenReturn(file(101L));
        when(files.uploadTempFile(any(), eq(7L), eq(SysFile.RefType.TRAVEL_MEMORY_IMAGE))).thenReturn(file(102L));
        var result = service.upload(upload, 7L);
        assertEquals(102L, result.getId());
        assertEquals("/api/files/102", result.getUrl());
        assertEquals("/api/files/101", result.getVideoUrl());
        assertEquals("phone.mov", result.getName());
    }

    @Test
    void oldImageCommandsAndNewVideoCommandsRoundTrip() {
        TravelMemoryConverter converter = Mappers.getMapper(TravelMemoryConverter.class);
        var command = new TravelMemoryEntryUpsertCommand();
        command.setImageUrl("/api/files/102");
        assertNull(converter.toEntity(command).getVideoUrl());
        command.setVideoUrl("/api/files/101");
        var entity = converter.toEntity(command);
        assertEquals(command.getVideoUrl(), entity.getVideoUrl());
        assertEquals(command.getVideoUrl(), converter.toEntryVO(entity).getVideoUrl());
        assertEquals(command.getImageUrl(), converter.toEntryVO(entity).getImageUrl());
    }

    private SysFile file(long id) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setFileUrl("/api/files/" + id);
        file.setFileSize(123L);
        return file;
    }
}
