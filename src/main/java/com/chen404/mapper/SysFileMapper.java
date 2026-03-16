package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统文件Mapper
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 查询需要清理的过期临时文件
     */
    @Select("SELECT * FROM sys_file WHERE status = 'TEMP' AND expire_time < NOW() AND deleted = 0")
    List<SysFile> selectExpiredTempFiles();

    /**
     * 根据URL查询文件
     */
    @Select("SELECT * FROM sys_file WHERE file_url = #{url} AND deleted = 0 LIMIT 1")
    SysFile selectByUrl(@Param("url") String url);

    /**
     * 根据对象名称查询文件
     */
    @Select("SELECT * FROM sys_file WHERE object_name = #{objectName} AND deleted = 0 LIMIT 1")
    SysFile selectByObjectName(@Param("objectName") String objectName);

    /**
     * 更新文件状态为永久
     */
    @Update("UPDATE sys_file SET status = 'PERMANENT', ref_type = #{refType}, ref_id = #{refId}, expire_time = NULL, update_time = NOW() WHERE file_url = #{fileUrl} AND deleted = 0")
    int updateToPermanent(@Param("fileUrl") String fileUrl, @Param("refType") String refType, @Param("refId") Long refId);

    /**
     * 批量更新文件状态为永久
     */
    int batchUpdateToPermanent(@Param("urls") List<String> urls, @Param("refType") String refType, @Param("refId") Long refId);

    /**
     * 查询文章关联的所有文件
     */
    @Select("SELECT * FROM sys_file WHERE ref_id = #{articleId} AND ref_type LIKE 'ARTICLE_%' AND deleted = 0")
    List<SysFile> selectFilesByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据对象名称列表查询文件
     */
    List<SysFile> selectByObjectNames(@Param("objectNames") List<String> objectNames);
}
