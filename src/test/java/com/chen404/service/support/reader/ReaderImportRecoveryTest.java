package com.chen404.service.support.reader;

import com.chen404.config.ReaderImportProperties;
import com.chen404.mapper.ReaderBookMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReaderImportRecoveryTest {
    @Test
    void retriesPendingBooksOnNextScanAfterQueueRejection() {
        ReaderBookMapper mapper = mock(ReaderBookMapper.class);
        ReaderImportTaskRunner runner = mock(ReaderImportTaskRunner.class);
        ReaderImportProperties properties = new ReaderImportProperties();
        properties.setBatchSize(2);
        when(mapper.selectRecoverableImports(2)).thenReturn(List.of(1L, 2L));
        doThrow(new RejectedExecutionException()).doNothing().when(runner).runAsync(1L);
        var recovery = new ReaderImportTaskRecovery(mapper, runner, properties, mock(ScheduledExecutorService.class));

        recovery.recoverPendingImports();
        verify(runner, never()).runAsync(2L);
        recovery.recoverPendingImports();
        verify(runner, times(2)).runAsync(1L);
        verify(runner).runAsync(2L);
        verify(mapper, times(2)).selectRecoverableImports(2);
    }

    @Test
    void databaseFailureDoesNotEscapeAndStopScheduledPolling() {
        ReaderBookMapper mapper = mock(ReaderBookMapper.class);
        when(mapper.selectRecoverableImports(anyInt())).thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(3L));
        ReaderImportTaskRunner runner = mock(ReaderImportTaskRunner.class);
        var recovery = new ReaderImportTaskRecovery(mapper, runner, new ReaderImportProperties(),
                mock(ScheduledExecutorService.class));
        assertDoesNotThrow(recovery::recoverPendingImports);
        recovery.recoverPendingImports();
        verify(runner).runAsync(3L);
    }

    @Test
    void deduplicatesQueuedTasksAndReleasesRegistrationAfterExecution() {
        List<Runnable> queue = new ArrayList<>();
        ReaderBookImportProcessor processor = mock(ReaderBookImportProcessor.class);
        var runner = new ReaderImportTaskRunner(processor, queue::add);
        runner.runAsync(1L);
        runner.runAsync(1L);
        assertEquals(1, queue.size());
        queue.remove(0).run();
        runner.runAsync(1L);
        assertEquals(1, queue.size());
        verify(processor).process(1L);
    }

    @Test
    void rejectedSubmissionCanBeScheduledAgain() {
        Executor executor = mock(Executor.class);
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        var runner = new ReaderImportTaskRunner(mock(ReaderBookImportProcessor.class), executor);
        assertThrows(RejectedExecutionException.class, () -> runner.runAsync(1L));
        assertDoesNotThrow(() -> runner.runAsync(1L));
        verify(executor, times(2)).execute(any());
    }
}
