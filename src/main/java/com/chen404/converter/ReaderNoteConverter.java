package com.chen404.converter;

import com.chen404.domain.dto.ReaderNoteVO;
import com.chen404.domain.entity.ReaderNote;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 阅读笔记实体与私有视图转换器。
 */
@Mapper(componentModel = "spring")
public interface ReaderNoteConverter {

    /**
     * 将笔记实体转换为接口视图，动态定位状态由服务层补充。
     *
     * @param note 笔记实体
     * @return 笔记视图
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "bookId", source = "bookId")
    @Mapping(target = "chapterId", source = "chapterId")
    @Mapping(target = "chapterOrder", source = "chapterOrder")
    @Mapping(target = "chapterTitle", source = "chapterTitle")
    @Mapping(target = "startBlockIndex", source = "startBlockIndex")
    @Mapping(target = "startCharacterOffset", source = "startCharacterOffset")
    @Mapping(target = "endBlockIndex", source = "endBlockIndex")
    @Mapping(target = "endCharacterOffset", source = "endCharacterOffset")
    @Mapping(target = "excerpt", source = "excerpt")
    @Mapping(target = "reflection", source = "reflection")
    @Mapping(target = "highlightColor", source = "highlightColor")
    @Mapping(target = "prefixContext", source = "prefixContext")
    @Mapping(target = "suffixContext", source = "suffixContext")
    @Mapping(target = "contentVersion", source = "contentVersion")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "targetChapterId", ignore = true)
    @Mapping(target = "contentChanged", ignore = true)
    ReaderNoteVO toVO(ReaderNote note);
}
