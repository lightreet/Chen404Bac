package com.chen404.converter;

import com.chen404.domain.dto.EmojiImportResultDTO;
import com.chen404.domain.dto.EmojiItemVO;
import com.chen404.domain.dto.EmojiPackVO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 表情包边界对象转换器。
 */
@Mapper(componentModel = "spring")
public interface EmojiConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "packCode", source = "packCode")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "iconUrl", source = "iconUrl")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "sort", source = "sort")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    EmojiPackVO toPackVO(EmojiPack pack);

    List<EmojiPackVO> toPackVOList(List<EmojiPack> packs);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "packCode", source = "packCode")
    @Mapping(target = "shortcode", source = "shortcode")
    @Mapping(target = "label", source = "label")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "unicode", source = "unicode")
    @Mapping(target = "assetUrl", source = "assetUrl")
    @Mapping(target = "width", source = "width")
    @Mapping(target = "height", source = "height")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "sort", source = "sort")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    EmojiItemVO toItemVO(EmojiItem item);

    List<EmojiItemVO> toItemVOList(List<EmojiItem> items);

    default EmojiImportResultDTO toImportResultDTO(
            String packCode,
            int successCount,
            int failCount,
            List<Map<String, String>> errors
    ) {
        EmojiImportResultDTO dto = new EmojiImportResultDTO();
        dto.setPackCode(packCode);
        dto.setSuccessCount(successCount);
        dto.setFailCount(failCount);
        dto.setErrors(toImportErrorList(errors));
        return dto;
    }

    default List<EmojiImportResultDTO.EmojiImportErrorDTO> toImportErrorList(List<Map<String, String>> errors) {
        if (errors == null || errors.isEmpty()) {
            return Collections.emptyList();
        }
        return errors.stream().map(this::toImportErrorDTO).collect(Collectors.toList());
    }

    default EmojiImportResultDTO.EmojiImportErrorDTO toImportErrorDTO(Map<String, String> source) {
        EmojiImportResultDTO.EmojiImportErrorDTO dto = new EmojiImportResultDTO.EmojiImportErrorDTO();
        if (source == null || source.isEmpty()) {
            return dto;
        }
        dto.setShortcode(source.get("shortcode"));
        dto.setError(source.get("error"));
        return dto;
    }
}
