package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.ReaderNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 阅读笔记持久化入口。
 */
@Mapper
public interface ReaderNoteMapper extends BaseMapper<ReaderNote> {
}
