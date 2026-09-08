package com.chen404.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chen404.config.MinioConfig;
import com.chen404.domain.entity.FileDeletionTask;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.SysFile;
import com.chen404.mapper.FileDeletionTaskMapper;
import com.chen404.mapper.FileReferenceMapper;
import com.chen404.mapper.SysFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 提交后清理对象存储；持久化删除状态和任务使失败、重启均可重试。 */
@Slf4j
@Service
public class FileDeletionService {
    private static final int BATCH_SIZE = 50;
    private static final int LEASE_MINUTES = 5;
    private static final int RETRY_SECONDS = 60;

    private final FileDeletionTaskMapper taskMapper;
    private final SysFileMapper fileMapper;
    private final FileReferenceMapper referenceMapper;
    private final FileStorageService storage;
    private final MinioConfig minioConfig;
    private final TransactionTemplate transaction;

    public FileDeletionService(FileDeletionTaskMapper taskMapper, SysFileMapper fileMapper,
            FileReferenceMapper referenceMapper, FileStorageService storage, MinioConfig minioConfig,
            PlatformTransactionManager transactionManager) {
        this.taskMapper = taskMapper;
        this.fileMapper = fileMapper;
        this.referenceMapper = referenceMapper;
        this.storage = storage;
        this.minioConfig = minioConfig;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 必须参加调用方业务事务，禁止先提交删除意图再修改业务引用。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long fileId) {
        taskMapper.enqueue(fileId);
    }

    /** 有界扫描；只有已经提交的任务才会进入独立清理事务。 */
    public void processPending() {
        for (FileDeletionTask task : taskMapper.selectDue(BATCH_SIZE)) {
            try {
                SysFile file = transaction.execute(status -> reserve(task.getFileId()));
                if (file != null) {
                    deleteReservedFile(file);
                }
            } catch (RuntimeException ex) {
                log.error("[FILE_DELETE_TASK_FAIL] fileId={}", task.getFileId(), ex);
            }
        }
    }

    /** 与引用写入共用行锁；删除中状态必须先提交，再调用不可回滚的存储操作。 */
    private SysFile reserve(Long fileId) {
        SysFile file = fileMapper.selectByIdForUpdate(fileId);
        FileDeletionTask task = taskMapper.selectById(fileId);
        if (task == null || task.getNextAttemptAt().isAfter(LocalDateTime.now())) {
            return null;
        }
        if (file == null || SysFile.Status.DELETED.equals(file.getStatus())) {
            taskMapper.deleteById(fileId);
            return null;
        }
        if (referenceMapper.selectCount(new LambdaQueryWrapper<FileReference>()
                .eq(FileReference::getFileId, fileId)) > 0) {
            taskMapper.deleteById(fileId);
            log.info("[FILE_DELETE_REFERENCED_SKIP] fileId={}", fileId);
            return null;
        }
        fileMapper.update(null, new LambdaUpdateWrapper<SysFile>()
                .eq(SysFile::getId, fileId).set(SysFile::getStatus, SysFile.Status.DELETING));
        task.setAttempts(task.getAttempts() + 1);
        task.setNextAttemptAt(LocalDateTime.now().plusMinutes(LEASE_MINUTES));
        taskMapper.updateById(task);
        return file;
    }

    /** 存储调用不占数据库事务；失败保留任务和删除中状态，禁止重新引用。 */
    private void deleteReservedFile(SysFile file) {
        boolean deleted;
        try {
            String bucket = StringUtils.hasText(file.getBucketName())
                    ? file.getBucketName() : minioConfig.getBucketName();
            deleted = storage.deleteFile(bucket, file.getObjectName());
        } catch (RuntimeException ex) {
            log.warn("[FILE_DELETE_STORAGE_FAIL] fileId={}", file.getId(), ex);
            deleted = false;
        }
        if (!deleted) {
            transaction.executeWithoutResult(status -> taskMapper.update(null,
                    new LambdaUpdateWrapper<FileDeletionTask>().eq(FileDeletionTask::getFileId, file.getId())
                            .set(FileDeletionTask::getNextAttemptAt, LocalDateTime.now().plusSeconds(RETRY_SECONDS))));
            log.warn("[FILE_DELETE_RETRY] fileId={}", file.getId());
            return;
        }
        transaction.executeWithoutResult(status -> {
            fileMapper.update(null, new LambdaUpdateWrapper<SysFile>()
                    .eq(SysFile::getId, file.getId()).set(SysFile::getStatus, SysFile.Status.DELETED));
            taskMapper.deleteById(file.getId());
        });
        log.info("[FILE_DELETE_OK] fileId={}", file.getId());
    }
}
