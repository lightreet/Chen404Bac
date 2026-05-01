package com.chen404.converter;

import com.chen404.domain.dto.CategoryVO;
import com.chen404.domain.dto.CreateCategoryCommand;
import com.chen404.domain.dto.UpdateCategoryCommand;
import com.chen404.domain.entity.Category;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 分类命令对象/视图对象与实体转换器。
 */
@Mapper(componentModel = "spring")
public interface CategoryConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "status", source = "status")
    Category toEntity(CreateCategoryCommand command);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "status", source = "status")
    Category toEntity(UpdateCategoryCommand command);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "articleCount", source = "articleCount")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    CategoryVO toVO(Category category);

    List<CategoryVO> toVOList(List<Category> categories);
}
