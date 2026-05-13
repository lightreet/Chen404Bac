package com.chen404.service;

import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;

import java.util.List;

/**
 * 旅行纪念地图服务接口，统一处理地点聚合、权限过滤与图片关联。
 */
public interface TravelMemoryService {

    /**
     * 查询当前用户可见的旅行纪念地点列表。
     */
    List<TravelMemoryLocation> listVisibleLocations(Long userId);

    /**
     * 查询当前用户可见的旅行纪念地点详情。
     */
    TravelMemoryLocation getVisibleLocationDetail(Long id, Long userId);

    /**
     * 查询管理员后台用的旅行纪念地点详情列表。
     */
    List<TravelMemoryLocation> listAdminLocations();

    /**
     * 创建旅行纪念地点及其照片条目。
     */
    TravelMemoryLocation createLocation(TravelMemoryLocation location, List<TravelMemoryEntry> entries, Long adminId);

    /**
     * 更新旅行纪念地点及其照片条目。
     */
    TravelMemoryLocation updateLocation(Long id, TravelMemoryLocation location, List<TravelMemoryEntry> entries, Long adminId);

    /**
     * 删除旅行纪念地点及其照片条目。
     */
    void deleteLocation(Long id, Long adminId);
}
