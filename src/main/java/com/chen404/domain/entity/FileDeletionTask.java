package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/** 文件删除持久化任务，与业务引用变更一同提交或回滚。 */
@Data
public class FileDeletionTask {
    @TableId(type = IdType.INPUT)
    private Long fileId;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime createTime;
}
