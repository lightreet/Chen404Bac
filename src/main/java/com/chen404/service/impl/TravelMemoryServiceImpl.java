package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.enums.TravelMemoryGeoSourceEnum;
import com.chen404.domain.enums.TravelMemoryStatusEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.TravelMemoryEntryMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.service.AccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.TravelMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 旅行纪念地图服务实现，负责地点聚合、照片维护、权限校验与文件清理。
 */
@Slf4j
@Service
public class TravelMemoryServiceImpl implements TravelMemoryService {

    private static final int DEFAULT_SORT_ORDER = 0;
    private static final int STATUS_VISIBLE = TravelMemoryStatusEnum.VISIBLE.getValue();
    private static final int COVER_YES = 1;
    private static final int COVER_NO = 0;

    private final TravelMemoryLocationMapper travelMemoryLocationMapper;
    private final TravelMemoryEntryMapper travelMemoryEntryMapper;
    private final AccessService accessService;
    private final SysFileService sysFileService;

    public TravelMemoryServiceImpl(
            TravelMemoryLocationMapper travelMemoryLocationMapper,
            TravelMemoryEntryMapper travelMemoryEntryMapper,
            AccessService accessService,
            SysFileService sysFileService) {
        this.travelMemoryLocationMapper = travelMemoryLocationMapper;
        this.travelMemoryEntryMapper = travelMemoryEntryMapper;
        this.accessService = accessService;
        this.sysFileService = sysFileService;
    }

    @Override
    public List<TravelMemoryLocation> listVisibleLocations(Long userId) {
        ensureCanView(userId);
        List<TravelMemoryLocation> locations = queryLocations(true);
        attachEntries(locations);
        return locations;
    }

    @Override
    public TravelMemoryLocation getVisibleLocationDetail(Long id, Long userId) {
        ensureCanView(userId);
        TravelMemoryLocation location = travelMemoryLocationMapper.selectById(id);
        if (location == null || !Objects.equals(location.getStatus(), STATUS_VISIBLE)) {
            return null;
        }
        attachEntries(List.of(location));
        return location;
    }

    @Override
    public List<TravelMemoryLocation> listAdminLocations() {
        List<TravelMemoryLocation> locations = queryLocations(false);
        attachEntries(locations);
        return locations;
    }

    @Override
    public TravelMemoryLocation getAdminLocationDetail(Long id, Long adminId) {
        ensureCanManage(adminId);
        return getAdminLocationOrThrow(id, adminId);
    }

    @Override
    @Transactional
    public TravelMemoryLocation createLocation(TravelMemoryLocation location, List<TravelMemoryEntry> entries, Long adminId) {
        ensureCanManage(adminId);
        TravelMemoryLocation normalizedLocation = normalizeLocation(location, entries, adminId, null);
        travelMemoryLocationMapper.insert(normalizedLocation);

        saveEntries(normalizedLocation.getId(), normalizedLocation.getEntries());
        convertEntryImagesToPermanent(normalizedLocation.getEntries(), normalizedLocation.getId());
        log.info("[TRAVEL_MEMORY_CREATE] adminId={} locationId={} entryCount={}",
                adminId, normalizedLocation.getId(), normalizedLocation.getEntries().size());
        return getAdminLocationOrThrow(normalizedLocation.getId(), adminId);
    }

    @Override
    @Transactional
    public TravelMemoryLocation updateLocation(Long id, TravelMemoryLocation location, List<TravelMemoryEntry> entries, Long adminId) {
        ensureCanManage(adminId);
        TravelMemoryLocation existing = getAdminLocationOrThrow(id, adminId);

        List<TravelMemoryEntry> oldEntries = listEntriesByLocationIds(List.of(id)).getOrDefault(id, List.of());
        TravelMemoryLocation normalizedLocation = normalizeLocation(location, entries, adminId, existing);
        Set<String> newUrls = normalizedLocation.getEntries().stream()
                .map(TravelMemoryEntry::getImageUrl)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        normalizedLocation.setId(id);
        travelMemoryLocationMapper.updateById(normalizedLocation);

        replaceEntries(id, normalizedLocation.getEntries());
        convertEntryImagesToPermanent(normalizedLocation.getEntries(), id);
        scheduleRemovedEntryImagesCleanup(oldEntries, newUrls, adminId);
        log.info("[TRAVEL_MEMORY_UPDATE] adminId={} locationId={} entryCount={}",
                adminId, id, normalizedLocation.getEntries().size());
        return getAdminLocationOrThrow(id, adminId);
    }

    @Override
    @Transactional
    public void deleteLocation(Long id, Long adminId) {
        ensureCanManage(adminId);
        getAdminLocationOrThrow(id, adminId);

        List<TravelMemoryEntry> entries = listEntriesByLocationIds(List.of(id)).getOrDefault(id, List.of());

        LambdaUpdateWrapper<TravelMemoryEntry> entryDelete = new LambdaUpdateWrapper<>();
        entryDelete.eq(TravelMemoryEntry::getLocationId, id)
                .set(TravelMemoryEntry::getDeleted, 1);
        travelMemoryEntryMapper.update(null, entryDelete);

        travelMemoryLocationMapper.deleteById(id);
        scheduleRemovedEntryImagesCleanup(entries, Set.of(), adminId);
        log.info("[TRAVEL_MEMORY_DELETE] adminId={} locationId={}", adminId, id);
    }

    private void ensureCanView(Long userId) {
        if (!accessService.canViewTravelMemory(userId)) {
            throw new ForbiddenException("仅管理员和知友可访问旅行纪念地图");
        }
    }

    private void ensureCanManage(Long userId) {
        if (!accessService.canManageTravelMemory(userId)) {
            throw new ForbiddenException("仅管理员可管理旅行纪念地图");
        }
    }

    private List<TravelMemoryLocation> queryLocations(boolean visibleOnly) {
        LambdaQueryWrapper<TravelMemoryLocation> wrapper = new LambdaQueryWrapper<>();
        if (visibleOnly) {
            wrapper.eq(TravelMemoryLocation::getStatus, STATUS_VISIBLE);
        }
        wrapper.orderByAsc(TravelMemoryLocation::getSortOrder)
                .orderByDesc(TravelMemoryLocation::getVisitedAt)
                .orderByDesc(TravelMemoryLocation::getId);
        return travelMemoryLocationMapper.selectList(wrapper);
    }

    private void attachEntries(List<TravelMemoryLocation> locations) {
        if (locations == null || locations.isEmpty()) {
            return;
        }
        List<Long> ids = locations.stream()
                .map(TravelMemoryLocation::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<TravelMemoryEntry>> entryMap = listEntriesByLocationIds(ids);
        for (TravelMemoryLocation location : locations) {
            List<TravelMemoryEntry> entries = new ArrayList<>(entryMap.getOrDefault(location.getId(), List.of()));
            entries.sort(Comparator
                    .comparing(TravelMemoryEntry::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(TravelMemoryEntry::getId, Comparator.nullsLast(Long::compareTo)));
            location.setEntries(entries);
            location.setEntryCount(entries.size());
            if (!StringUtils.hasText(location.getCoverImage())) {
                location.setCoverImage(resolveCoverImage(entries));
            }
        }
    }

    private Map<Long, List<TravelMemoryEntry>> listEntriesByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<TravelMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TravelMemoryEntry::getLocationId, locationIds)
                .orderByAsc(TravelMemoryEntry::getDisplayOrder)
                .orderByAsc(TravelMemoryEntry::getId);
        List<TravelMemoryEntry> rows = travelMemoryEntryMapper.selectList(wrapper);
        Map<Long, List<TravelMemoryEntry>> entryMap = new LinkedHashMap<>();
        for (TravelMemoryEntry row : rows) {
            entryMap.computeIfAbsent(row.getLocationId(), key -> new ArrayList<>()).add(row);
        }
        return entryMap;
    }

    private TravelMemoryLocation normalizeLocation(
            TravelMemoryLocation input,
            List<TravelMemoryEntry> entries,
            Long adminId,
            TravelMemoryLocation existing) {
        Long locationId = existing != null ? existing.getId() : null;
        if (input == null) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=input_null",
                    adminId, locationId);
            throw new BadRequestException("旅行纪念地点不能为空");
        }
        List<TravelMemoryEntry> normalizedEntries = normalizeEntries(entries, adminId, locationId);

        input.setStatus(TravelMemoryStatusEnum.normalizeValue(input.getStatus()));
        input.setSortOrder(input.getSortOrder() == null ? DEFAULT_SORT_ORDER : input.getSortOrder());
        input.setCoverImage(resolveCoverImage(normalizedEntries));
        fillDisplayCoordinates(input, normalizedEntries);

        if (existing != null) {
            input.setCreatedBy(existing.getCreatedBy());
            input.setCreateTime(existing.getCreateTime());
        } else {
            input.setCreatedBy(adminId);
        }
        input.setUpdatedBy(adminId);
        input.setEntries(normalizedEntries);
        input.setEntryCount(normalizedEntries.size());
        return input;
    }

    private List<TravelMemoryEntry> normalizeEntries(List<TravelMemoryEntry> entries, Long adminId, Long locationId) {
        if (entries == null || entries.isEmpty()) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=entries_empty",
                    adminId, locationId);
            throw new BadRequestException("至少需要保留一张照片");
        }

        List<TravelMemoryEntry> normalized = new ArrayList<>();
        boolean coverAssigned = false;
        for (int index = 0; index < entries.size(); index++) {
            TravelMemoryEntry entry = entries.get(index);
            if (entry == null || !StringUtils.hasText(entry.getImageUrl())) {
                continue;
            }
            entry.setImageUrl(entry.getImageUrl().trim());
            entry.setDisplayOrder(entry.getDisplayOrder() == null ? index : entry.getDisplayOrder());
            entry.setGeoSource(TravelMemoryGeoSourceEnum.normalizeCode(entry.getGeoSource()));
            if (Integer.valueOf(COVER_YES).equals(entry.getIsCover()) && !coverAssigned) {
                entry.setIsCover(COVER_YES);
                coverAssigned = true;
            } else {
                entry.setIsCover(COVER_NO);
            }
            normalized.add(entry);
        }

        if (normalized.isEmpty()) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=entries_normalized_empty",
                    adminId, locationId);
            throw new BadRequestException("至少需要保留一张照片");
        }
        if (!coverAssigned) {
            normalized.get(0).setIsCover(COVER_YES);
        }
        return normalized;
    }

    private void fillDisplayCoordinates(TravelMemoryLocation location, List<TravelMemoryEntry> entries) {
        if (location.getLatitude() != null && location.getLongitude() != null) {
            return;
        }
        for (TravelMemoryEntry entry : entries) {
            BigDecimal lat = entry.getSourceLatitude();
            BigDecimal lng = entry.getSourceLongitude();
            if (lat != null && lng != null) {
                location.setLatitude(lat);
                location.setLongitude(lng);
                return;
            }
        }
        throw new BadRequestException("请先为旅行地点选择地图坐标");
    }

    private String resolveCoverImage(List<TravelMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        for (TravelMemoryEntry entry : entries) {
            if (Integer.valueOf(COVER_YES).equals(entry.getIsCover()) && StringUtils.hasText(entry.getImageUrl())) {
                return entry.getImageUrl().trim();
            }
        }
        return StringUtils.hasText(entries.get(0).getImageUrl()) ? entries.get(0).getImageUrl().trim() : "";
    }

    private void saveEntries(Long locationId, List<TravelMemoryEntry> entries) {
        for (TravelMemoryEntry entry : entries) {
            entry.setId(null);
            entry.setLocationId(locationId);
            travelMemoryEntryMapper.insert(entry);
        }
    }

    private void replaceEntries(Long locationId, List<TravelMemoryEntry> entries) {
        LambdaUpdateWrapper<TravelMemoryEntry> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(TravelMemoryEntry::getLocationId, locationId)
                .set(TravelMemoryEntry::getDeleted, 1);
        travelMemoryEntryMapper.update(null, deleteWrapper);
        saveEntries(locationId, entries);
    }

    private void convertEntryImagesToPermanent(List<TravelMemoryEntry> entries, Long locationId) {
        List<String> urls = entries.stream()
                .map(TravelMemoryEntry::getImageUrl)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        sysFileService.convertToPermanent(urls, SysFile.RefType.TRAVEL_MEMORY_IMAGE, locationId);
    }

    private void scheduleRemovedEntryImagesCleanup(List<TravelMemoryEntry> oldEntries, Set<String> newUrls, Long adminId) {
        List<String> urlsToDelete = oldEntries.stream()
                .map(TravelMemoryEntry::getImageUrl)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(url -> !newUrls.contains(url))
                .distinct()
                .toList();
        if (urlsToDelete.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupRemovedEntryImages(urlsToDelete, adminId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupRemovedEntryImages(urlsToDelete, adminId);
            }
        });
    }

    private void cleanupRemovedEntryImages(List<String> urlsToDelete, Long adminId) {
        for (String oldUrl : urlsToDelete) {
            try {
                sysFileService.deleteByUrl(oldUrl, adminId);
            } catch (Exception ex) {
                log.warn("[TRAVEL_MEMORY_IMAGE_DELETE_FAIL] adminId={} url={} message={}",
                        adminId, oldUrl, ex.getMessage(), ex);
            }
        }
    }

    private TravelMemoryLocation loadLocationDetail(Long id) {
        TravelMemoryLocation location = travelMemoryLocationMapper.selectById(id);
        if (location == null) {
            return null;
        }
        attachEntries(List.of(location));
        return location;
    }

    private TravelMemoryLocation getAdminLocationOrThrow(Long id, Long adminId) {
        TravelMemoryLocation location = loadLocationDetail(id);
        if (location == null) {
            log.warn("[TRAVEL_MEMORY_NOT_FOUND] adminId={} locationId={}", adminId, id);
            throw new ResourceNotFoundException("旅行纪念地点不存在");
        }
        return location;
    }
}
