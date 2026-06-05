package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.TravelMemoryStop;
import com.chen404.domain.enums.TravelMemoryGeoSourceEnum;
import com.chen404.domain.enums.TravelMemoryStatusEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.TravelMemoryEntryMapper;
import com.chen404.mapper.TravelMemoryLocationMapper;
import com.chen404.mapper.TravelMemoryStopMapper;
import com.chen404.service.AccessService;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import com.chen404.service.TravelMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 旅行记忆地图服务实现。
 */
@Slf4j
@Service
public class TravelMemoryServiceImpl implements TravelMemoryService {

    private static final int DEFAULT_SORT_ORDER = 0;
    private static final int STATUS_VISIBLE = TravelMemoryStatusEnum.VISIBLE.getValue();
    private static final int COVER_YES = 1;
    private static final int COVER_NO = 0;
    private static final int STOP_COVER_YES = 1;
    private static final int STOP_COVER_NO = 0;

    private final TravelMemoryLocationMapper travelMemoryLocationMapper;
    private final TravelMemoryStopMapper travelMemoryStopMapper;
    private final TravelMemoryEntryMapper travelMemoryEntryMapper;
    private final AccessService accessService;
    private final SysFileService sysFileService;
    private final FileReferenceService fileReferenceService;

    public TravelMemoryServiceImpl(
            TravelMemoryLocationMapper travelMemoryLocationMapper,
            TravelMemoryStopMapper travelMemoryStopMapper,
            TravelMemoryEntryMapper travelMemoryEntryMapper,
            AccessService accessService,
            SysFileService sysFileService,
            FileReferenceService fileReferenceService) {
        this.travelMemoryLocationMapper = travelMemoryLocationMapper;
        this.travelMemoryStopMapper = travelMemoryStopMapper;
        this.travelMemoryEntryMapper = travelMemoryEntryMapper;
        this.accessService = accessService;
        this.sysFileService = sysFileService;
        this.fileReferenceService = fileReferenceService;
    }

    @Override
    public List<TravelMemoryLocation> listVisibleLocations(Long userId) {
        ensureCanView(userId);
        List<TravelMemoryLocation> locations = queryLocations(true);
        attachStructuredChildren(locations);
        return locations;
    }

    @Override
    public TravelMemoryLocation getVisibleLocationDetail(Long id, Long userId) {
        ensureCanView(userId);
        TravelMemoryLocation location = travelMemoryLocationMapper.selectById(id);
        if (location == null || !Objects.equals(location.getStatus(), STATUS_VISIBLE)) {
            return null;
        }
        attachStructuredChildren(List.of(location));
        return location;
    }

    @Override
    public List<TravelMemoryLocation> listAdminLocations() {
        List<TravelMemoryLocation> locations = queryLocations(false);
        attachStructuredChildren(locations);
        return locations;
    }

    @Override
    public TravelMemoryLocation getAdminLocationDetail(Long id, Long adminId) {
        ensureCanManage(adminId);
        return getAdminLocationOrThrow(id, adminId);
    }

    @Override
    @Transactional
    public TravelMemoryLocation createLocation(
            TravelMemoryLocation location,
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> legacyEntries,
            Long adminId) {
        ensureCanManage(adminId);
        TravelMemoryLocation normalizedLocation = normalizeLocation(location, stops, legacyEntries, adminId, null);
        travelMemoryLocationMapper.insert(normalizedLocation);

        saveStops(normalizedLocation.getId(), normalizedLocation.getStops());
        convertEntryImagesToPermanent(normalizedLocation.getEntries(), normalizedLocation.getId());
        fileReferenceService.syncTravelMemoryReferences(
                normalizedLocation.getId(),
                normalizedLocation.getCoverImage(),
                normalizedLocation.getEntries()
        );
        log.info("[TRAVEL_MEMORY_CREATE] adminId={} locationId={} stopCount={} entryCount={}",
                adminId,
                normalizedLocation.getId(),
                normalizedLocation.getStops().size(),
                normalizedLocation.getEntries().size());
        return getAdminLocationOrThrow(normalizedLocation.getId(), adminId);
    }

    @Override
    @Transactional
    public TravelMemoryLocation updateLocation(
            Long id,
            TravelMemoryLocation location,
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> legacyEntries,
            Long adminId) {
        ensureCanManage(adminId);
        TravelMemoryLocation existing = getAdminLocationOrThrow(id, adminId);

        List<TravelMemoryEntry> oldEntries = listEntriesByLocationIds(List.of(id)).getOrDefault(id, List.of());
        TravelMemoryLocation normalizedLocation = normalizeLocation(location, stops, legacyEntries, adminId, existing);
        Set<String> newUrls = normalizedLocation.getEntries().stream()
                .map(TravelMemoryEntry::getImageUrl)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        normalizedLocation.setId(id);
        travelMemoryLocationMapper.updateById(normalizedLocation);

        fileReferenceService.removeByOwners(
                com.chen404.domain.entity.FileReference.ModuleCode.TRAVEL_MEMORY_ENTRY,
                com.chen404.domain.entity.FileReference.BizType.TRAVEL_MEMORY_ENTRY_IMAGE,
                oldEntries.stream().map(TravelMemoryEntry::getId).filter(Objects::nonNull).toList()
        );
        replaceStops(id, normalizedLocation.getStops());
        convertEntryImagesToPermanent(normalizedLocation.getEntries(), id);
        fileReferenceService.syncTravelMemoryReferences(id, normalizedLocation.getCoverImage(), normalizedLocation.getEntries());
        scheduleRemovedEntryImagesCleanup(oldEntries, newUrls, adminId);
        log.info("[TRAVEL_MEMORY_UPDATE] adminId={} locationId={} stopCount={} entryCount={}",
                adminId,
                id,
                normalizedLocation.getStops().size(),
                normalizedLocation.getEntries().size());
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

        LambdaUpdateWrapper<TravelMemoryStop> stopDelete = new LambdaUpdateWrapper<>();
        stopDelete.eq(TravelMemoryStop::getLocationId, id)
                .set(TravelMemoryStop::getDeleted, 1);
        travelMemoryStopMapper.update(null, stopDelete);

        fileReferenceService.removeByOwner(
                com.chen404.domain.entity.FileReference.ModuleCode.TRAVEL_MEMORY,
                com.chen404.domain.entity.FileReference.BizType.TRAVEL_MEMORY_COVER,
                id
        );
        fileReferenceService.removeByOwners(
                com.chen404.domain.entity.FileReference.ModuleCode.TRAVEL_MEMORY_ENTRY,
                com.chen404.domain.entity.FileReference.BizType.TRAVEL_MEMORY_ENTRY_IMAGE,
                entries.stream().map(TravelMemoryEntry::getId).filter(Objects::nonNull).toList()
        );
        travelMemoryLocationMapper.deleteById(id);
        scheduleRemovedEntryImagesCleanup(entries, Set.of(), adminId);
        log.info("[TRAVEL_MEMORY_DELETE] adminId={} locationId={}", adminId, id);
    }

    private void ensureCanView(Long userId) {
        if (!accessService.canViewTravelMemory(userId)) {
            throw new ForbiddenException("仅管理员和知友可访问旅行记忆地图");
        }
    }

    private void ensureCanManage(Long userId) {
        if (!accessService.canManageTravelMemory(userId)) {
            throw new ForbiddenException("仅管理员可管理旅行记忆地图");
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

    private void attachStructuredChildren(List<TravelMemoryLocation> locations) {
        if (locations == null || locations.isEmpty()) {
            return;
        }
        List<Long> ids = locations.stream()
                .map(TravelMemoryLocation::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<TravelMemoryStop>> stopMap = listStopsByLocationIds(ids);
        Map<Long, List<TravelMemoryEntry>> entryMap = listEntriesByLocationIds(ids);

        for (TravelMemoryLocation location : locations) {
            List<TravelMemoryStop> stops = new ArrayList<>(stopMap.getOrDefault(location.getId(), List.of()));
            stops.sort(Comparator
                    .comparing(TravelMemoryStop::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(TravelMemoryStop::getId, Comparator.nullsLast(Long::compareTo)));

            List<TravelMemoryEntry> locationEntries = new ArrayList<>(entryMap.getOrDefault(location.getId(), List.of()));
            locationEntries.sort(Comparator
                    .comparing(TravelMemoryEntry::getStopId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(TravelMemoryEntry::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(TravelMemoryEntry::getId, Comparator.nullsLast(Long::compareTo)));

            Map<Long, List<TravelMemoryEntry>> stopEntriesMap = locationEntries.stream()
                    .filter(entry -> entry.getStopId() != null)
                    .collect(Collectors.groupingBy(
                            TravelMemoryEntry::getStopId,
                            LinkedHashMap::new,
                            Collectors.toCollection(ArrayList::new)
                    ));

            List<TravelMemoryEntry> flattenedEntries = new ArrayList<>();
            for (TravelMemoryStop stop : stops) {
                List<TravelMemoryEntry> stopEntries = new ArrayList<>(stopEntriesMap.getOrDefault(stop.getId(), List.of()));
                stopEntries.sort(Comparator
                        .comparing(TravelMemoryEntry::getDisplayOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TravelMemoryEntry::getId, Comparator.nullsLast(Long::compareTo)));
                normalizeStopCover(stopEntries);
                stop.setEntries(stopEntries);
                stop.setEntryCount(stopEntries.size());
                if (!StringUtils.hasText(stop.getCoverImage())) {
                    stop.setCoverImage(resolveStopCoverImage(stopEntries));
                }
                flattenedEntries.addAll(stopEntries);
            }

            if (stops.isEmpty() && !locationEntries.isEmpty()) {
                TravelMemoryStop fallbackStop = buildLegacyFallbackStop(location, locationEntries);
                stops = List.of(fallbackStop);
                flattenedEntries = new ArrayList<>(locationEntries);
            }

            location.setStops(stops);
            location.setEntries(flattenedEntries);
            location.setEntryCount(flattenedEntries.size());
            if (!StringUtils.hasText(location.getCoverImage())) {
                location.setCoverImage(resolveLocationCoverImage(flattenedEntries));
            }
        }
    }

    private Map<Long, List<TravelMemoryStop>> listStopsByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<TravelMemoryStop> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TravelMemoryStop::getLocationId, locationIds)
                .orderByAsc(TravelMemoryStop::getSortOrder)
                .orderByAsc(TravelMemoryStop::getId);
        List<TravelMemoryStop> rows = travelMemoryStopMapper.selectList(wrapper);
        Map<Long, List<TravelMemoryStop>> stopMap = new LinkedHashMap<>();
        for (TravelMemoryStop row : rows) {
            stopMap.computeIfAbsent(row.getLocationId(), key -> new ArrayList<>()).add(row);
        }
        return stopMap;
    }

    private Map<Long, List<TravelMemoryEntry>> listEntriesByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<TravelMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TravelMemoryEntry::getLocationId, locationIds)
                .orderByAsc(TravelMemoryEntry::getStopId)
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
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> legacyEntries,
            Long adminId,
            TravelMemoryLocation existing) {
        Long locationId = existing != null ? existing.getId() : null;
        if (input == null) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=input_null", adminId, locationId);
            throw new BadRequestException("旅行记忆地点不能为空");
        }

        input.setTitle(trimToNull(input.getTitle()));
        if (!StringUtils.hasText(input.getTitle())) {
            throw new BadRequestException("旅行记忆标题不能为空");
        }
        input.setProvince(trimToEmpty(input.getProvince()));
        input.setCity(trimToEmpty(input.getCity()));
        input.setSummaryNote(trimToEmpty(input.getSummaryNote()));

        validateTravelDateRange(input, adminId, locationId);

        List<TravelMemoryStop> normalizedStops = normalizeStops(input, stops, legacyEntries, adminId, locationId);
        List<TravelMemoryEntry> flattenedEntries = flattenEntries(normalizedStops);

        input.setStatus(TravelMemoryStatusEnum.normalizeValue(input.getStatus()));
        input.setSortOrder(input.getSortOrder() == null ? DEFAULT_SORT_ORDER : input.getSortOrder());
        input.setCoverImage(resolveLocationCoverImage(flattenedEntries));
        fillDisplayCoordinates(input, normalizedStops, flattenedEntries);

        if (existing != null) {
            input.setCreatedBy(existing.getCreatedBy());
            input.setCreateTime(existing.getCreateTime());
        } else {
            input.setCreatedBy(adminId);
        }
        input.setUpdatedBy(adminId);
        input.setStops(normalizedStops);
        input.setEntries(flattenedEntries);
        input.setEntryCount(flattenedEntries.size());
        return input;
    }

    private void validateTravelDateRange(TravelMemoryLocation input, Long adminId, Long locationId) {
        LocalDateTime visitedAt = input.getVisitedAt();
        LocalDateTime visitedEndAt = input.getVisitedEndAt();
        if (visitedAt == null || visitedEndAt == null || !visitedEndAt.isBefore(visitedAt)) {
            return;
        }

        log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=invalid_date_range", adminId, locationId);
        throw new BadRequestException("旅行结束日期不能早于开始日期");
    }

    private List<TravelMemoryStop> normalizeStops(
            TravelMemoryLocation input,
            List<TravelMemoryStop> providedStops,
            List<TravelMemoryEntry> legacyEntries,
            Long adminId,
            Long locationId) {
        List<TravelMemoryStop> normalizedStops = new ArrayList<>();

        if (providedStops != null && !providedStops.isEmpty()) {
            for (TravelMemoryStop stop : providedStops) {
                if (stop == null) {
                    continue;
                }
                stop.setTitle(trimToNull(stop.getTitle()));
                if (!StringUtils.hasText(stop.getTitle())) {
                    throw new BadRequestException("每个旅途片段都需要标题");
                }
                stop.setStoryNote(trimToEmpty(stop.getStoryNote()));
                List<TravelMemoryEntry> stopEntries = normalizeEntries(stop.getEntries(), adminId, locationId, true);
                stop.setEntries(stopEntries);
                stop.setEntryCount(stopEntries.size());
                normalizedStops.add(stop);
            }
        } else if (legacyEntries != null && !legacyEntries.isEmpty()) {
            TravelMemoryStop fallbackStop = new TravelMemoryStop();
            fallbackStop.setTitle(resolveFallbackStopTitle(input, locationId));
            fallbackStop.setStoryNote(trimToEmpty(input.getSummaryNote()));
            fallbackStop.setVisitedAt(input.getVisitedAt());
            fallbackStop.setLatitude(input.getLatitude());
            fallbackStop.setLongitude(input.getLongitude());
            fallbackStop.setEntries(normalizeEntries(legacyEntries, adminId, locationId, false));
            fallbackStop.setEntryCount(fallbackStop.getEntries().size());
            normalizedStops.add(fallbackStop);
        }

        if (normalizedStops.isEmpty()) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=stops_empty", adminId, locationId);
            throw new BadRequestException("至少需要保留一个旅途片段");
        }

        for (int stopIndex = 0; stopIndex < normalizedStops.size(); stopIndex++) {
            TravelMemoryStop stop = normalizedStops.get(stopIndex);
            stop.setSortOrder(stopIndex);
            normalizeCoordinatePair(stop::getLatitude, stop::getLongitude, stop::setLatitude, stop::setLongitude);
            normalizeStopCover(stop.getEntries());
            stop.setCoverImage(resolveStopCoverImage(stop.getEntries()));
            for (int entryIndex = 0; entryIndex < stop.getEntries().size(); entryIndex++) {
                stop.getEntries().get(entryIndex).setDisplayOrder(entryIndex);
            }
        }

        normalizeLocationCover(normalizedStops);
        return normalizedStops;
    }

    private List<TravelMemoryEntry> normalizeEntries(
            List<TravelMemoryEntry> entries,
            Long adminId,
            Long locationId,
            boolean segmentScopedMessage) {
        if (entries == null || entries.isEmpty()) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=entries_empty", adminId, locationId);
            throw new BadRequestException(segmentScopedMessage ? "每个片段至少需要保留一张照片" : "至少需要保留一张照片");
        }

        List<TravelMemoryEntry> normalized = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            TravelMemoryEntry entry = entries.get(index);
            if (entry == null || !StringUtils.hasText(entry.getImageUrl())) {
                continue;
            }
            entry.setImageUrl(entry.getImageUrl().trim());
            entry.setRemark(trimToEmpty(entry.getRemark()));
            entry.setThanksNote(trimToEmpty(entry.getThanksNote()));
            entry.setDisplayOrder(entry.getDisplayOrder() == null ? index : entry.getDisplayOrder());
            entry.setGeoSource(TravelMemoryGeoSourceEnum.normalizeCode(entry.getGeoSource()));
            entry.setIsCover(Integer.valueOf(COVER_YES).equals(entry.getIsCover()) ? COVER_YES : COVER_NO);
            entry.setIsStopCover(Integer.valueOf(STOP_COVER_YES).equals(entry.getIsStopCover()) ? STOP_COVER_YES : STOP_COVER_NO);
            normalized.add(entry);
        }

        if (normalized.isEmpty()) {
            log.warn("[TRAVEL_MEMORY_BAD_REQUEST] adminId={} locationId={} reason=entries_normalized_empty", adminId, locationId);
            throw new BadRequestException(segmentScopedMessage ? "每个片段至少需要保留一张照片" : "至少需要保留一张照片");
        }
        return normalized;
    }

    private void normalizeStopCover(List<TravelMemoryEntry> entries) {
        boolean stopCoverAssigned = false;
        for (TravelMemoryEntry entry : entries) {
            if (Integer.valueOf(STOP_COVER_YES).equals(entry.getIsStopCover()) && !stopCoverAssigned) {
                entry.setIsStopCover(STOP_COVER_YES);
                stopCoverAssigned = true;
            } else {
                entry.setIsStopCover(STOP_COVER_NO);
            }
        }
        if (!stopCoverAssigned && !entries.isEmpty()) {
            entries.get(0).setIsStopCover(STOP_COVER_YES);
        }
    }

    private void normalizeLocationCover(List<TravelMemoryStop> stops) {
        TravelMemoryEntry firstEntry = null;
        boolean coverAssigned = false;
        for (TravelMemoryStop stop : stops) {
            for (TravelMemoryEntry entry : stop.getEntries()) {
                if (firstEntry == null) {
                    firstEntry = entry;
                }
                if (Integer.valueOf(COVER_YES).equals(entry.getIsCover()) && !coverAssigned) {
                    entry.setIsCover(COVER_YES);
                    coverAssigned = true;
                } else {
                    entry.setIsCover(COVER_NO);
                }
            }
        }
        if (!coverAssigned && firstEntry != null) {
            firstEntry.setIsCover(COVER_YES);
        }
    }

    private List<TravelMemoryEntry> flattenEntries(List<TravelMemoryStop> stops) {
        List<TravelMemoryEntry> flattenedEntries = new ArrayList<>();
        for (TravelMemoryStop stop : stops) {
            flattenedEntries.addAll(stop.getEntries());
        }
        return flattenedEntries;
    }

    private void fillDisplayCoordinates(
            TravelMemoryLocation location,
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> entries) {
        normalizeCoordinatePair(location::getLatitude, location::getLongitude, location::setLatitude, location::setLongitude);
        if (location.getLatitude() != null && location.getLongitude() != null) {
            return;
        }
        for (TravelMemoryStop stop : stops) {
            if (stop.getLatitude() != null && stop.getLongitude() != null) {
                location.setLatitude(stop.getLatitude());
                location.setLongitude(stop.getLongitude());
                return;
            }
        }
        for (TravelMemoryEntry entry : entries) {
            BigDecimal latitude = entry.getSourceLatitude();
            BigDecimal longitude = entry.getSourceLongitude();
            if (latitude != null && longitude != null) {
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                return;
            }
        }
        throw new BadRequestException("请先为旅行地点选择地图坐标");
    }

    private String resolveLocationCoverImage(List<TravelMemoryEntry> entries) {
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

    private String resolveStopCoverImage(List<TravelMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        for (TravelMemoryEntry entry : entries) {
            if (Integer.valueOf(STOP_COVER_YES).equals(entry.getIsStopCover()) && StringUtils.hasText(entry.getImageUrl())) {
                return entry.getImageUrl().trim();
            }
        }
        return StringUtils.hasText(entries.get(0).getImageUrl()) ? entries.get(0).getImageUrl().trim() : "";
    }

    private void saveStops(Long locationId, List<TravelMemoryStop> stops) {
        for (TravelMemoryStop stop : stops) {
            stop.setId(null);
            stop.setLocationId(locationId);
            travelMemoryStopMapper.insert(stop);
            saveEntries(locationId, stop.getId(), stop.getEntries());
        }
    }

    private void saveEntries(Long locationId, Long stopId, List<TravelMemoryEntry> entries) {
        for (TravelMemoryEntry entry : entries) {
            entry.setId(null);
            entry.setLocationId(locationId);
            entry.setStopId(stopId);
            travelMemoryEntryMapper.insert(entry);
        }
    }

    private void replaceStops(Long locationId, List<TravelMemoryStop> stops) {
        LambdaUpdateWrapper<TravelMemoryEntry> entryDelete = new LambdaUpdateWrapper<>();
        entryDelete.eq(TravelMemoryEntry::getLocationId, locationId)
                .set(TravelMemoryEntry::getDeleted, 1);
        travelMemoryEntryMapper.update(null, entryDelete);

        LambdaUpdateWrapper<TravelMemoryStop> stopDelete = new LambdaUpdateWrapper<>();
        stopDelete.eq(TravelMemoryStop::getLocationId, locationId)
                .set(TravelMemoryStop::getDeleted, 1);
        travelMemoryStopMapper.update(null, stopDelete);

        saveStops(locationId, stops);
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
        attachStructuredChildren(List.of(location));
        return location;
    }

    private TravelMemoryLocation getAdminLocationOrThrow(Long id, Long adminId) {
        TravelMemoryLocation location = loadLocationDetail(id);
        if (location == null) {
            log.warn("[TRAVEL_MEMORY_NOT_FOUND] adminId={} locationId={}", adminId, id);
            throw new ResourceNotFoundException("旅行记忆地点不存在");
        }
        return location;
    }

    private TravelMemoryStop buildLegacyFallbackStop(TravelMemoryLocation location, List<TravelMemoryEntry> entries) {
        TravelMemoryStop stop = new TravelMemoryStop();
        stop.setTitle(resolveFallbackStopTitle(location, location.getId()));
        stop.setStoryNote(trimToEmpty(location.getSummaryNote()));
        stop.setVisitedAt(location.getVisitedAt());
        stop.setLatitude(location.getLatitude());
        stop.setLongitude(location.getLongitude());
        stop.setSortOrder(0);
        stop.setEntries(new ArrayList<>(entries));
        stop.setEntryCount(entries.size());
        normalizeStopCover(stop.getEntries());
        stop.setCoverImage(resolveStopCoverImage(stop.getEntries()));
        return stop;
    }

    private String resolveFallbackStopTitle(TravelMemoryLocation input, Long locationId) {
        String title = trimToNull(input.getTitle());
        if (StringUtils.hasText(title)) {
            return title;
        }
        return locationId == null ? "旅途片段" : "旅途片段 " + locationId;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimToEmpty(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private interface DecimalGetter {
        BigDecimal get();
    }

    private interface DecimalSetter {
        void set(BigDecimal value);
    }

    private void normalizeCoordinatePair(
            DecimalGetter latitudeGetter,
            DecimalGetter longitudeGetter,
            DecimalSetter latitudeSetter,
            DecimalSetter longitudeSetter) {
        BigDecimal latitude = latitudeGetter.get();
        BigDecimal longitude = longitudeGetter.get();
        if ((latitude == null) != (longitude == null)) {
            latitudeSetter.set(null);
            longitudeSetter.set(null);
        }
    }
}
