# 文件删除事务边界

文章图片替换、头像替换、小说删除、旅行图片移除及过期临时文件清理，均在业务事务内写入 `file_deletion_task`，不直接删除对象存储。业务回滚时任务一起回滚。删除 API 返回“已提交删除”，实际清理由后台完成。

本地调度器默认启动 30 秒后执行，每轮最多处理 50 条，轮次间隔 30 秒，不依赖 XXL-JOB。`file.cleanup.initial-delay-ms` 和 `file.cleanup.delay-ms` 可调整调度间隔。

任务与文件认领、引用写入共用 `sys_file` 行锁。发现仍有 `file_reference` 引用时取消清理；无引用则先提交 `DELETING` 状态及 5 分钟租约，再在数据库事务外删除对象。删除中的文件不能重新认领、引用或签发下载地址。成功后写入 `DELETED` 并移除任务；失败后保留删除屏障，60 秒后重试。进程退出或删除后数据库提交失败，租约到期后再次执行幂等的对象删除。

排查积压可查询 `file_deletion_task` 的 `attempts`、`next_attempt_at`，结合 `FILE_DELETE_RETRY`、`FILE_DELETE_STORAGE_FAIL`、`FILE_DELETE_TASK_FAIL` 日志定位存储或数据库故障。不要通过手工恢复 `PERMANENT` 状态重用删除中的文件，对象可能已经删除。

`FileDeletionServiceTest` 使用 H2 MySQL 兼容模式执行实际 Mapper SQL 和数据库事务，覆盖回滚、引用竞争、重试和工作实例重建；它不替代目标 MySQL 环境的迁移部署验证。
