package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.ReaderBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 书籍持久化及跨实例导入任务认领。 */
@Mapper
public interface ReaderBookMapper extends BaseMapper<ReaderBook> {
    /** 分批查询未认领、待重试或租约过期的任务。 */
    List<Long> selectRecoverableImports(@Param("limit") int limit);

    /** 仅在没有有效租约时认领，返回 1 表示获得执行权。 */
    int tryClaimImport(@Param("bookId") Long bookId, @Param("token") String token,
                      @Param("leaseSeconds") int leaseSeconds);

    int renewImportLease(@Param("bookId") Long bookId, @Param("token") String token,
                         @Param("leaseSeconds") int leaseSeconds);

    /** 调用方必须在事务内持有此锁，直到章节和书籍状态一起提交。 */
    ReaderBook selectClaimForUpdate(@Param("bookId") Long bookId, @Param("token") String token);

    /** 失败结果必须属于当前有效租约；可恢复错误延迟重试，超出次数或坏文件终止。 */
    int retryOrFailImport(@Param("bookId") Long bookId, @Param("token") String token,
                          @Param("message") String message, @Param("terminal") boolean terminal,
                          @Param("maxAttempts") int maxAttempts, @Param("retrySeconds") int retrySeconds);
}
