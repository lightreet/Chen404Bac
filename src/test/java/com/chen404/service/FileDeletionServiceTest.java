package com.chen404.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chen404.config.MinioConfig;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.FileDeletionTaskMapper;
import com.chen404.mapper.FileReferenceMapper;
import com.chen404.mapper.SysFileMapper;
import com.chen404.service.impl.SysFileServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实数据库事务和实际 Mapper SQL 验证清理队列，不连接业务数据库。 */
@SpringJUnitConfig(FileDeletionServiceTest.TestConfig.class)
class FileDeletionServiceTest {
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private FileDeletionService deletionService;
    @Autowired private SysFileService sysFileService;
    @Autowired private FileStorageService storage;
    @Autowired private AccessService access;
    @Autowired private FileDeletionTaskMapper taskMapper;
    @Autowired private SysFileMapper fileMapper;
    @Autowired private FileReferenceMapper referenceMapper;
    @Autowired private MinioConfig minioConfig;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE sys_file (id BIGINT PRIMARY KEY, file_name VARCHAR(255), "
                + "file_original_name VARCHAR(255), object_name VARCHAR(255), storage_scope VARCHAR(32), "
                + "bucket_name VARCHAR(255), file_path VARCHAR(255), file_url VARCHAR(255), file_size BIGINT, "
                + "content_type VARCHAR(128), user_id BIGINT, status VARCHAR(32), ref_type VARCHAR(64), "
                + "ref_id BIGINT, expire_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE file_reference (id BIGINT AUTO_INCREMENT PRIMARY KEY, file_id BIGINT)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/"
                + "V2026090801__create_file_deletion_task.sql")));
        jdbc.update("INSERT INTO sys_file (id, object_name, bucket_name, file_url, user_id, status, ref_type, ref_id) "
                + "VALUES (1, 'image.png', 'protected', '/api/files/1', 7, 'PERMANENT', 'ARTICLE_CONTENT', 10)");
        reset(storage, access);
        when(access.canDeleteFile(eq(7L), any())).thenReturn(true);
        when(storage.deleteFile(anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "物理删除不能占用或参加业务数据库事务");
            assertEquals("DELETING", fileStatus(), "删除屏障必须先持久化");
            return true;
        });
    }

    @Test
    void articleFailureAfterCleanupMustRollBackTaskAndPreserveFile() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            assertEquals(1, sysFileService.cleanUnusedFiles(10L, "new content", null));
            assertEquals(1, taskCount());
            throw new IllegalStateException("subsequent file claim failed");
        }));
        deletionService.processPending();
        assertEquals(0, taskCount());
        assertEquals("PERMANENT", fileStatus());
        verifyNoInteractions(storage);
    }

    @Test
    void avatarOrBookDeleteMustShareCallerRollback() {
        transaction.executeWithoutResult(status -> {
            assertTrue(sysFileService.deleteByUrl("/api/files/1", 7L));
            status.setRollbackOnly();
        });
        deletionService.processPending();
        assertEquals(0, taskCount());
        verifyNoInteractions(storage);
    }

    @Test
    void shouldDeleteOnlyAfterCommitAndDeduplicateRepeatedRequests() {
        transaction.executeWithoutResult(status -> {
            sysFileService.deleteByUrl("/api/files/1", 7L);
            sysFileService.deleteByUrl("/api/files/1", 7L);
            verifyNoInteractions(storage);
            assertEquals(1, taskCount());
        });
        deletionService.processPending();
        deletionService.processPending();
        assertEquals("DELETED", fileStatus());
        assertEquals(0, taskCount());
        verify(storage, times(1)).deleteFile("protected", "image.png");
    }

    @Test
    void shouldRecheckReferencesAddedBeforeBusinessCommit() {
        transaction.executeWithoutResult(status -> {
            sysFileService.cleanUnusedFiles(10L, "", null);
            sysFileService.lockForReference(1L);
            jdbc.update("INSERT INTO file_reference (file_id) VALUES (1)");
        });
        deletionService.processPending();
        assertEquals("PERMANENT", fileStatus());
        assertEquals(0, taskCount());
        verifyNoInteractions(storage);
    }

    @Test
    void shouldKeepFailureForRetryAcrossWorkerRestartAndRejectNewReferences() {
        doReturn(false, true).when(storage).deleteFile(anyString(), anyString());
        sysFileService.deleteByUrl("/api/files/1", 7L);
        deletionService.processPending();
        assertEquals("DELETING", fileStatus());
        assertEquals(1, taskCount());
        assertEquals(1, taskMapper.selectById(1L).getAttempts());
        assertThrows(BadRequestException.class, () -> transaction.executeWithoutResult(
                status -> sysFileService.lockForReference(1L)));
        assertThrows(BadRequestException.class, () -> sysFileService.claimPermanentFiles(
                7L, List.of(new FileClaim(1L, null)), "ARTICLE_CONTENT", 10L));

        deletionService.processPending();
        verify(storage, times(1)).deleteFile(anyString(), anyString());
        makeDue();
        new FileDeletionService(taskMapper, fileMapper, referenceMapper, storage, minioConfig,
                transactionManager).processPending();
        assertEquals(0, taskCount());
        assertEquals("DELETED", fileStatus());
        verify(storage, times(2)).deleteFile(anyString(), anyString());
    }

    @Test
    void shouldRetryStorageExceptionsWithoutLosingIntent() {
        doThrow(new IllegalStateException("storage unavailable")).when(storage).deleteFile(anyString(), anyString());
        sysFileService.deleteByUrl("/api/files/1", 7L);
        deletionService.processPending();
        assertEquals(1, taskCount());
        assertEquals("DELETING", fileStatus());
    }

    @Test
    void shouldScheduleExpiredTemporaryFilesWithoutDeletingDuringScan() {
        jdbc.update("UPDATE sys_file SET status='TEMP', ref_id=NULL, expire_time=TIMESTAMP '2000-01-01 00:00:00'");
        assertEquals(1, sysFileService.cleanExpiredTempFiles());
        verifyNoInteractions(storage);
        deletionService.processPending();
        assertEquals("DELETED", fileStatus());
    }

    private void makeDue() {
        jdbc.update("UPDATE file_deletion_task SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'");
    }

    @Test
    void shouldWaitForConcurrentReferenceWriterBeforeDecidingToDelete() throws Exception {
        sysFileService.deleteByUrl("/api/files/1", 7L);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            sysFileService.lockForReference(1L);
            jdbc.update("INSERT INTO file_reference (file_id) VALUES (1)");
            locked.countDown();
            try {
                assertTrue(commit.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }));
        assertTrue(locked.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> cleaner = CompletableFuture.runAsync(deletionService::processPending);
        try {
            assertThrows(java.util.concurrent.TimeoutException.class, () -> cleaner.get(100, TimeUnit.MILLISECONDS));
            verifyNoInteractions(storage);
        } finally {
            commit.countDown();
        }
        writer.get(5, TimeUnit.SECONDS);
        cleaner.get(5, TimeUnit.SECONDS);
        assertEquals("PERMANENT", fileStatus());
        assertEquals(0, taskCount());
        verifyNoInteractions(storage);
    }

    private int taskCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM file_deletion_task", Integer.class);
    }

    private String fileStatus() {
        return jdbc.queryForObject("SELECT status FROM sys_file WHERE id=1", String.class);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:file-deletion;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.addMapper(SysFileMapper.class);
            configuration.addMapper(FileReferenceMapper.class);
            configuration.addMapper(FileDeletionTaskMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean SysFileMapper fileMapper(SqlSessionTemplate session) { return session.getMapper(SysFileMapper.class); }
        @Bean FileReferenceMapper referenceMapper(SqlSessionTemplate session) { return session.getMapper(FileReferenceMapper.class); }
        @Bean FileDeletionTaskMapper taskMapper(SqlSessionTemplate session) { return session.getMapper(FileDeletionTaskMapper.class); }
        @Bean FileStorageService storage() { return mock(FileStorageService.class); }
        @Bean AccessService access() { return mock(AccessService.class); }
        @Bean ImageProcessingService imageProcessing() { return mock(ImageProcessingService.class); }
        @Bean ManagedFileUrlCodec codec() { return new ManagedFileUrlCodec("file-deletion-test-secret", 5); }
        @Bean MinioConfig minioConfig() { return new MinioConfig(); }
        @Bean SysFileServiceImpl sysFileService() { return new SysFileServiceImpl(); }
        @Bean FileDeletionService deletionService(FileDeletionTaskMapper tasks, SysFileMapper files,
                FileReferenceMapper references, FileStorageService storage, MinioConfig config,
                PlatformTransactionManager manager) {
            return new FileDeletionService(tasks, files, references, storage, config, manager);
        }
    }
}
