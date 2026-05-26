package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.domain.dto.AdminFileStatsBucketVO;
import com.chen404.domain.dto.AdminFileStatsVO;
import com.chen404.domain.dto.AdminFileVO;
import com.chen404.domain.entity.FileReference;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.PageResult;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.chen404.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminFileServiceImplTest {

    @Test
    void shouldAggregateFullFileStatistics() {
        initTableInfo(SysFile.class);
        initTableInfo(FileReference.class);

        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        UserService userService = mock(UserService.class);
        AdminFileServiceImpl service = new AdminFileServiceImpl(sysFileService, fileReferenceService, userService);

        SysFile referenced = file(1L, SysFile.Status.PERMANENT, SysFile.RefType.SITE_ASSET, 100L);
        SysFile pending = file(2L, SysFile.Status.TEMP, SysFile.RefType.ARTICLE_CONTENT, 250L);
        SysFile unreferenced = file(3L, SysFile.Status.PERMANENT, SysFile.RefType.ARTICLE_CONTENT, 300L);
        SysFile deleted = file(4L, SysFile.Status.DELETED, SysFile.RefType.SITE_HERO, 50L);
        when(sysFileService.list(org.mockito.ArgumentMatchers.<Wrapper<SysFile>>any()))
                .thenReturn(List.of(referenced, pending, unreferenced, deleted));

        when(fileReferenceService.list(org.mockito.ArgumentMatchers.<Wrapper<FileReference>>any()))
                .thenReturn(List.of(
                        reference(1L, FileReference.ModuleCode.ARTICLE),
                        reference(1L, FileReference.ModuleCode.SITE_CONFIG),
                        reference(4L, FileReference.ModuleCode.TRAVEL_MEMORY)
                ));

        AdminFileStatsVO result = service.getAdminFileStats();

        assertEquals(4L, result.getTotalFiles());
        assertEquals(700L, result.getTotalSize());
        assertEquals(1L, result.getReferencedCount());
        assertEquals(1L, result.getPendingCount());
        assertEquals(1L, result.getUnreferencedCount());
        assertEquals(1L, result.getDeletedCount());
        assertEquals(3L, result.getReferenceRecordCount());

        assertBucketCounts(result.getStatusBuckets(),
                List.of("REFERENCED", "PENDING", "UNREFERENCED", "DELETED"),
                List.of(1L, 1L, 1L, 1L));
        assertBucketCounts(result.getRefTypeBuckets(),
                List.of(SysFile.RefType.ARTICLE_CONTENT, SysFile.RefType.SITE_ASSET, SysFile.RefType.SITE_HERO),
                List.of(2L, 1L, 1L));
    }

    @Test
    void shouldFilterFilesByReferenceStatusBeforePagination() {
        initTableInfo(SysFile.class);
        initTableInfo(FileReference.class);

        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        UserService userService = mock(UserService.class);
        AdminFileServiceImpl service = new AdminFileServiceImpl(sysFileService, fileReferenceService, userService);

        SysFile referenced = file(1L, SysFile.Status.PERMANENT, SysFile.RefType.SITE_ASSET, 100L);
        SysFile pending = file(2L, SysFile.Status.TEMP, SysFile.RefType.ARTICLE_CONTENT, 250L);
        SysFile unreferenced = file(3L, SysFile.Status.PERMANENT, SysFile.RefType.ARTICLE_CONTENT, 300L);
        SysFile deleted = file(4L, SysFile.Status.DELETED, SysFile.RefType.SITE_HERO, 50L);
        when(sysFileService.list(org.mockito.ArgumentMatchers.<Wrapper<SysFile>>any()))
                .thenReturn(List.of(referenced, pending, unreferenced, deleted));
        when(fileReferenceService.list(org.mockito.ArgumentMatchers.<Wrapper<FileReference>>any()))
                .thenReturn(List.of(reference(1L, FileReference.ModuleCode.ARTICLE)));
        when(userService.listByIds(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());

        PageResult<AdminFileVO> result = service.getAdminFiles(1, 10, null, null, null, null, "UNREFERENCED");

        assertEquals(1L, result.getTotal());
        assertNotNull(result.getList());
        assertEquals(1, result.getList().size());
        assertEquals(3L, result.getList().get(0).getId());
        assertEquals("UNREFERENCED", result.getList().get(0).getReferenceStatus());
    }

    private void assertBucketCounts(List<AdminFileStatsBucketVO> buckets, List<String> expectedKeys, List<Long> expectedCounts) {
        assertIterableEquals(expectedKeys, buckets.stream().map(AdminFileStatsBucketVO::getKey).toList());
        assertIterableEquals(expectedCounts, buckets.stream().map(AdminFileStatsBucketVO::getCount).toList());
    }

    private SysFile file(Long id, String status, String refType, Long size) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setStatus(status);
        file.setRefType(refType);
        file.setFileSize(size);
        return file;
    }

    private FileReference reference(Long fileId, String moduleCode) {
        FileReference reference = new FileReference();
        reference.setFileId(fileId);
        reference.setModuleCode(moduleCode);
        return reference;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
