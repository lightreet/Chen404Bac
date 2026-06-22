package com.chen404.converter;

import com.chen404.domain.dto.CreateTravelMemoryCommand;
import com.chen404.domain.dto.TravelMemoryEntryUpsertCommand;
import com.chen404.domain.dto.TravelMemoryEntryVO;
import com.chen404.domain.dto.TravelMemoryLocationDetailVO;
import com.chen404.domain.dto.TravelMemoryLocationListItemVO;
import com.chen404.domain.dto.TravelMemoryStopUpsertCommand;
import com.chen404.domain.dto.TravelMemoryStopVO;
import com.chen404.domain.dto.UpdateTravelMemoryCommand;
import com.chen404.domain.entity.TravelMemoryEntry;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.domain.entity.TravelMemoryStop;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 旅行记忆命令对象、实体与视图对象转换器。
 */
@Mapper(componentModel = "spring")
public interface TravelMemoryConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "province", source = "province")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "summaryNote", source = "summaryNote")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "visitedEndAt", source = "visitedEndAt")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "sortOrder", source = "sortOrder")
    TravelMemoryLocation toEntity(CreateTravelMemoryCommand command);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "province", source = "province")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "summaryNote", source = "summaryNote")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "visitedEndAt", source = "visitedEndAt")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "sortOrder", source = "sortOrder")
    TravelMemoryLocation toEntity(UpdateTravelMemoryCommand command);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "remark", source = "remark")
    @Mapping(target = "thanksNote", source = "thanksNote")
    @Mapping(target = "shotAt", source = "shotAt")
    @Mapping(target = "displayOrder", source = "displayOrder")
    @Mapping(target = "sourceLatitude", source = "sourceLatitude")
    @Mapping(target = "sourceLongitude", source = "sourceLongitude")
    @Mapping(target = "geoSource", source = "geoSource")
    @Mapping(target = "isCover", ignore = true)
    @Mapping(target = "isStopCover", ignore = true)
    TravelMemoryEntry toEntity(TravelMemoryEntryUpsertCommand command);

    List<TravelMemoryEntry> toEntryEntityList(List<TravelMemoryEntryUpsertCommand> commands);

    @AfterMapping
    default void normalizeEntryCover(TravelMemoryEntryUpsertCommand command, @MappingTarget TravelMemoryEntry target) {
        target.setIsCover(Boolean.TRUE.equals(command.getCover()) ? 1 : 0);
        target.setIsStopCover(Boolean.TRUE.equals(command.getStopCover()) ? 1 : 0);
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "title", source = "title")
    @Mapping(target = "storyNote", source = "storyNote")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "coverImage", ignore = true)
    @Mapping(target = "entries", source = "entries")
    TravelMemoryStop toEntity(TravelMemoryStopUpsertCommand command);

    List<TravelMemoryStop> toStopEntityList(List<TravelMemoryStopUpsertCommand> commands);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "remark", source = "remark")
    @Mapping(target = "thanksNote", source = "thanksNote")
    @Mapping(target = "shotAt", source = "shotAt")
    @Mapping(target = "displayOrder", source = "displayOrder")
    @Mapping(target = "stopId", source = "stopId")
    @Mapping(target = "sourceLatitude", source = "sourceLatitude")
    @Mapping(target = "sourceLongitude", source = "sourceLongitude")
    @Mapping(target = "geoSource", source = "geoSource")
    @Mapping(target = "cover", ignore = true)
    @Mapping(target = "stopCover", ignore = true)
    TravelMemoryEntryVO toEntryVO(TravelMemoryEntry entry);

    List<TravelMemoryEntryVO> toEntryVOList(List<TravelMemoryEntry> entries);

    @AfterMapping
    default void fillEntryCover(TravelMemoryEntry source, @MappingTarget TravelMemoryEntryVO target) {
        target.setCover(source != null && Integer.valueOf(1).equals(source.getIsCover()));
        target.setStopCover(source != null && Integer.valueOf(1).equals(source.getIsStopCover()));
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "storyNote", source = "storyNote")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "entryCount", source = "entryCount")
    @Mapping(target = "entries", ignore = true)
    TravelMemoryStopVO toStopVO(TravelMemoryStop stop);

    List<TravelMemoryStopVO> toStopVOList(List<TravelMemoryStop> stops);

    @AfterMapping
    default void fillStopEntries(TravelMemoryStop source, @MappingTarget TravelMemoryStopVO target) {
        target.setEntries(toEntryVOList(source.getEntries()));
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "province", source = "province")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "summaryNote", source = "summaryNote")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "visitedEndAt", source = "visitedEndAt")
    @Mapping(target = "entryCount", source = "entryCount")
    @Mapping(target = "visibility", source = "visibility")
    TravelMemoryLocationListItemVO toListItemVO(TravelMemoryLocation location);

    List<TravelMemoryLocationListItemVO> toListItemVOList(List<TravelMemoryLocation> locations);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "province", source = "province")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "latitude", source = "latitude")
    @Mapping(target = "longitude", source = "longitude")
    @Mapping(target = "summaryNote", source = "summaryNote")
    @Mapping(target = "coverImage", source = "coverImage")
    @Mapping(target = "visitedAt", source = "visitedAt")
    @Mapping(target = "visitedEndAt", source = "visitedEndAt")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "entryCount", source = "entryCount")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "entries", ignore = true)
    @Mapping(target = "stops", ignore = true)
    TravelMemoryLocationDetailVO toDetailVO(TravelMemoryLocation location);

    @AfterMapping
    default void fillDetailChildren(TravelMemoryLocation source, @MappingTarget TravelMemoryLocationDetailVO target) {
        target.setEntries(toEntryVOList(source.getEntries()));
        target.setStops(toStopVOList(source.getStops()));
    }
}
