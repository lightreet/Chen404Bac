package com.chen404.converter;

import com.chen404.domain.dto.TagVO;
import com.chen404.domain.entity.Tag;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 标签视图转换器。
 */
@Mapper(componentModel = "spring")
public interface TagConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "color", source = "color")
    @Mapping(target = "articleCount", source = "articleCount")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "status", source = "status")
    TagVO toVO(Tag tag);

    List<TagVO> toVOList(List<Tag> tags);
}
