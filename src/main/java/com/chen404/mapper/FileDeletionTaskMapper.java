package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.FileDeletionTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 删除任务按文件去重，重复请求不会重置正在执行的租约。 */
@Mapper
public interface FileDeletionTaskMapper extends BaseMapper<FileDeletionTask> {
    @Insert("INSERT INTO file_deletion_task (file_id, attempts, next_attempt_at, create_time) "
            + "VALUES (#{fileId}, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE file_id = file_id")
    void enqueue(@Param("fileId") Long fileId);

    @Select("SELECT * FROM file_deletion_task WHERE next_attempt_at <= CURRENT_TIMESTAMP "
            + "ORDER BY next_attempt_at, file_id LIMIT #{limit}")
    List<FileDeletionTask> selectDue(@Param("limit") int limit);
}
