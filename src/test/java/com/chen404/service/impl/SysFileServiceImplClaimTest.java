package com.chen404.service.impl;

import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.mapper.SysFileMapper;
import com.chen404.service.FileClaim;
import com.chen404.service.ManagedFileUrlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysFileServiceImplClaimTest {

    private SysFileMapper sysFileMapper;
    private SysFileServiceImpl sysFileService;

    @BeforeEach
    void setUp() {
        sysFileMapper = mock(SysFileMapper.class);
        sysFileService = new SysFileServiceImpl();
        ReflectionTestUtils.setField(sysFileService, "baseMapper", sysFileMapper);
        ReflectionTestUtils.setField(
                sysFileService,
                "managedFileUrlCodec",
                new ManagedFileUrlCodec("test-file-claim-secret", 5)
        );
    }

    @Test
    void shouldRejectClaimWhenFileIdAndUrlPointToDifferentRows() {
        SysFile byId = buildTempFile(1L, 7L, "https://cdn.example.com/one.mp3");
        SysFile byUrl = buildTempFile(2L, 7L, "https://cdn.example.com/two.mp3");
        when(sysFileMapper.selectById(1L)).thenReturn(byId);
        when(sysFileMapper.selectByUrl(byUrl.getFileUrl())).thenReturn(byUrl);

        assertThrows(
                BadRequestException.class,
                () -> sysFileService.claimPermanentFiles(
                        7L,
                        List.of(FileClaim.byIdAndUrl(1L, byUrl.getFileUrl())),
                        SysFile.RefType.MUSIC_AUDIO,
                        10L
                )
        );
    }

    @Test
    void shouldRejectClaimFromAnotherUploader() {
        SysFile file = buildTempFile(1L, 8L, "https://cdn.example.com/audio.mp3");
        when(sysFileMapper.selectById(1L)).thenReturn(file);

        assertThrows(
                ForbiddenException.class,
                () -> sysFileService.claimPermanentFiles(
                        7L,
                        List.of(new FileClaim(1L, null)),
                        SysFile.RefType.MUSIC_AUDIO,
                        10L
                )
        );
    }

    @Test
    void shouldPermanentlyBindOwnedTemporaryFileToBusiness() {
        SysFile file = buildTempFile(1L, 7L, "https://cdn.example.com/audio.mp3");
        when(sysFileMapper.selectById(1L)).thenReturn(file);

        sysFileService.claimPermanentFiles(
                7L,
                List.of(new FileClaim(1L, null)),
                SysFile.RefType.MUSIC_AUDIO,
                10L
        );

        assertEquals(SysFile.Status.PERMANENT, file.getStatus());
        assertEquals(10L, file.getRefId());
        assertNull(file.getExpireTime());
        verify(sysFileMapper).updateById(file);
    }

    private SysFile buildTempFile(Long id, Long userId, String url) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setUserId(userId);
        file.setFileUrl(url);
        file.setStatus(SysFile.Status.TEMP);
        file.setRefType(SysFile.RefType.MUSIC_AUDIO);
        file.setExpireTime(LocalDateTime.now().plusHours(1));
        return file;
    }
}
