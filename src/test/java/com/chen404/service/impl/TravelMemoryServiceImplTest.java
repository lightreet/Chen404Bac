package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.TravelMemoryStop;
import com.chen404.domain.entity.User;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.TravelMemoryEntryMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.mapper.TravelMemoryStopMapper;
import com.chen404.service.AccessService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelMemoryServiceImplTest {

    @Test
    void shouldQueryLocationsBySortOrderThenVisitedAtThenId() {
        initTableInfo(TravelMemoryLocation.class);
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        when(accessService.canViewTravelMemory(1L)).thenReturn(true);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        service.listVisibleLocations(1L);

        ArgumentCaptor<LambdaQueryWrapper<TravelMemoryLocation>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(locationMapper).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("sort_order"), () -> "排序规则应包含 sort_order，实际为: " + sqlSegment);
        assertTrue(sqlSegment.contains("visited_at"), () -> "排序规则应包含 visited_at，实际为: " + sqlSegment);
        assertTrue(sqlSegment.contains("id"), () -> "排序规则应包含 id，实际为: " + sqlSegment);
    }

    @Test
    void shouldOnlyListPublicLocationsForAnonymousViewer() {
        initTableInfo(TravelMemoryLocation.class);
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        when(accessService.canViewTravelMemory(null)).thenReturn(true);
        when(locationMapper.selectList(any())).thenReturn(List.of());

        service.listVisibleLocations(null);

        ArgumentCaptor<LambdaQueryWrapper<TravelMemoryLocation>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(locationMapper).selectList(captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("status"), () -> "公开列表应先过滤展示状态，实际为: " + sqlSegment);
        assertTrue(sqlSegment.contains("visibility"), () -> "未登录列表应按公开可见性过滤，实际为: " + sqlSegment);
    }

    @Test
    void shouldHideFriendOnlyDetailFromAnonymousViewer() {
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        TravelMemoryLocation location = buildLocation(7L, "知友地点", LocalDateTime.of(2026, 5, 10, 10, 0));
        location.setVisibility(2);
        when(accessService.canViewTravelMemory(null)).thenReturn(true);
        when(locationMapper.selectById(7L)).thenReturn(location);

        assertNull(service.getVisibleLocationDetail(7L, null));
    }

    @Test
    void shouldShowFriendOnlyDetailToFriendViewer() {
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        TravelMemoryLocation location = buildLocation(7L, "知友地点", LocalDateTime.of(2026, 5, 10, 10, 0));
        location.setVisibility(2);
        User viewer = new User();
        when(accessService.canViewTravelMemory(2L)).thenReturn(true);
        when(accessService.getUserOrNull(2L)).thenReturn(viewer);
        when(accessService.isFriend(viewer)).thenReturn(true);
        when(locationMapper.selectById(7L)).thenReturn(location);
        when(stopMapper.selectList(any())).thenReturn(List.of());
        when(entryMapper.selectList(any())).thenReturn(List.of());

        assertSame(location, service.getVisibleLocationDetail(7L, 2L));
    }

    @Test
    void shouldDeleteRemovedImagesOnlyAfterCommit() {
        initTableInfo(TravelMemoryLocation.class);
        initTableInfo(TravelMemoryStop.class);
        initTableInfo(TravelMemoryEntry.class);
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        when(accessService.canManageTravelMemory(1L)).thenReturn(true);

        TravelMemoryLocation existing = buildLocation(7L, "旧地点", LocalDateTime.of(2026, 5, 10, 10, 0));
        TravelMemoryLocation updated = buildLocation(7L, "新地点", LocalDateTime.of(2026, 5, 11, 10, 0));
        AtomicInteger locationSelectCount = new AtomicInteger();
        when(locationMapper.selectById(7L)).thenAnswer(invocation ->
                locationSelectCount.getAndIncrement() == 0 ? existing : updated);
        when(locationMapper.updateById(any(TravelMemoryLocation.class))).thenReturn(1);
        when(stopMapper.selectList(any())).thenReturn(List.of());
        when(stopMapper.update(any(), any())).thenReturn(1);
        when(stopMapper.insert(any(TravelMemoryStop.class))).thenReturn(1);

        TravelMemoryEntry oldKeep = buildEntry(11L, 7L, "https://cdn.example.com/keep.jpg", 0, 1);
        TravelMemoryEntry oldRemove = buildEntry(12L, 7L, "https://cdn.example.com/remove.jpg", 1, 0);
        TravelMemoryEntry newKeep = buildEntry(21L, 7L, "https://cdn.example.com/keep.jpg", 0, 1);

        AtomicInteger entrySelectCount = new AtomicInteger();
        when(entryMapper.selectList(any())).thenAnswer(invocation -> {
            int current = entrySelectCount.getAndIncrement();
            if (current < 2) {
                return List.of(oldKeep, oldRemove);
            }
            return List.of(newKeep);
        });
        when(entryMapper.update(any(), any())).thenReturn(1);
        when(entryMapper.insert(any(TravelMemoryEntry.class))).thenReturn(1);
        when(sysFileService.deleteByUrl(any(), eq(1L))).thenReturn(true);

        TravelMemoryLocation commandLocation = buildLocation(null, "新地点", LocalDateTime.of(2026, 5, 11, 10, 0));
        commandLocation.setLatitude(existing.getLatitude());
        commandLocation.setLongitude(existing.getLongitude());
        commandLocation.setStatus(1);
        commandLocation.setSortOrder(3);
        TravelMemoryEntry commandEntry = buildEntry(null, null, "https://cdn.example.com/keep.jpg", 0, 1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateLocation(7L, commandLocation, List.of(), List.of(commandEntry), 1L);

            verify(sysFileService, never()).deleteByUrl("https://cdn.example.com/remove.jpg", 1L);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(sysFileService, times(1)).deleteByUrl("https://cdn.example.com/remove.jpg", 1L);
            verify(sysFileService, never()).deleteByUrl("https://cdn.example.com/keep.jpg", 1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingMissingLocation() {
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        when(accessService.canManageTravelMemory(1L)).thenReturn(true);
        when(locationMapper.selectById(99L)).thenReturn(null);

        TravelMemoryLocation commandLocation = buildLocation(null, "新地点", LocalDateTime.of(2026, 5, 11, 10, 0));
        TravelMemoryEntry commandEntry = buildEntry(null, null, "https://cdn.example.com/keep.jpg", 0, 1);

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateLocation(99L, commandLocation, List.of(), List.of(commandEntry), 1L));
    }

    @Test
    void shouldThrowBadRequestWhenEntriesMissing() {
        TravelMemoryLocationMapper locationMapper = mock(TravelMemoryLocationMapper.class);
        TravelMemoryStopMapper stopMapper = mock(TravelMemoryStopMapper.class);
        TravelMemoryEntryMapper entryMapper = mock(TravelMemoryEntryMapper.class);
        AccessService accessService = mock(AccessService.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        TravelMemoryServiceImpl service = new TravelMemoryServiceImpl(
                locationMapper,
                stopMapper,
                entryMapper,
                accessService,
                sysFileService,
                fileReferenceService);

        when(accessService.canManageTravelMemory(1L)).thenReturn(true);

        TravelMemoryLocation commandLocation = buildLocation(null, "空照片地点", LocalDateTime.of(2026, 5, 11, 10, 0));

        assertThrows(BadRequestException.class,
                () -> service.createLocation(commandLocation, List.of(), List.of(), 1L));
    }

    private TravelMemoryLocation buildLocation(Long id, String title, LocalDateTime visitedAt) {
        TravelMemoryLocation location = new TravelMemoryLocation();
        location.setId(id);
        location.setTitle(title);
        location.setProvince("福建省");
        location.setCity("厦门市");
        location.setLatitude(new java.math.BigDecimal("24.479800"));
        location.setLongitude(new java.math.BigDecimal("118.089400"));
        location.setVisitedAt(visitedAt);
        location.setStatus(1);
        location.setVisibility(2);
        location.setSortOrder(0);
        return location;
    }

    private TravelMemoryEntry buildEntry(Long id, Long locationId, String imageUrl, Integer displayOrder, Integer isCover) {
        TravelMemoryEntry entry = new TravelMemoryEntry();
        entry.setId(id);
        entry.setLocationId(locationId);
        entry.setImageUrl(imageUrl);
        entry.setRemark("remark");
        entry.setThanksNote("note");
        entry.setDisplayOrder(displayOrder);
        entry.setIsCover(isCover);
        entry.setGeoSource("MANUAL");
        return entry;
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
