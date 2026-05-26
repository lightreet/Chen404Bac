package com.chen404.job;

import com.chen404.service.FileReferenceService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileReferenceRebuildJobTest {

    @Test
    void shouldTriggerReferenceRebuildThroughJobHandler() {
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        when(fileReferenceService.rebuildAllReferences()).thenReturn(Map.of("references", 12));

        FileReferenceRebuildJob job = new FileReferenceRebuildJob(fileReferenceService);

        job.rebuildFileReferences();

        verify(fileReferenceService).rebuildAllReferences();
    }
}
