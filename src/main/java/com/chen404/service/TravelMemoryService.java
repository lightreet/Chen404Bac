package com.chen404.service;

import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.TravelMemoryStop;

import java.util.List;

/**
 * 旅行记忆地图服务接口。
 */
public interface TravelMemoryService {

    List<TravelMemoryLocation> listVisibleLocations(Long userId, Long creatorId);

    List<TravelMemoryLocation> listMyLocations(Long userId);

    TravelMemoryLocation getVisibleLocationDetail(Long id, Long userId);

    TravelMemoryLocation getManageableLocationDetail(Long id, Long userId);

    List<TravelMemoryLocation> listAdminLocations(Long adminId);

    TravelMemoryLocation getAdminLocationDetail(Long id, Long adminId);

    TravelMemoryLocation createLocation(
            TravelMemoryLocation location,
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> legacyEntries,
            Long operatorId);

    TravelMemoryLocation updateLocation(
            Long id,
            TravelMemoryLocation location,
            List<TravelMemoryStop> stops,
            List<TravelMemoryEntry> legacyEntries,
            Long operatorId);

    void deleteLocation(Long id, Long operatorId);
}
