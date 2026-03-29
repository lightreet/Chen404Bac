package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.SysFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 系统文件服务接口
 */
public interface SysFileService extends IService<SysFile> {

    /**
     * 上传临时文件（用于编辑器内上传，尚未确认使用）
     *
     * @param file     文件
     * @param userId   上传用户ID
     * @param refType  引用类型
     * @return 文件信息
     */
    SysFile uploadTempFile(MultipartFile file, Long userId, String refType);

    /**
     * 将临时文件转为永久文件（文章发布时调用）
     *
     * @param fileUrls 文件URL列表
     * @param refType  引用类型
     * @param refId    引用ID
     */
    void convertToPermanent(List<String> fileUrls, String refType, Long refId);

    /**
     * 根据URL删除文件
     *
     * @param fileUrl 文件URL
     * @param userId  操作用户ID
     * @return 是否成功
     */
    boolean deleteByUrl(String fileUrl, Long userId);

    /**
     * 清理过期临时文件
     *
     * @return 清理数量
     */
    int cleanExpiredTempFiles();

    /**
     * 提取文章内容中的所有图片URL
     *
     * @param content 文章内容（Markdown/HTML）
     * @return URL列表
     */
    List<String> extractImageUrlsFromContent(String content);

    /**
     * 清理文章未使用的文件（文章更新时调用）
     *
     * @param articleId   文章ID
     * @param newContent  新文章内容
     * @param newCoverUrl 新封面URL
     * @return 清理的文件数量
     */
    int cleanUnusedFiles(Long articleId, String newContent, String newCoverUrl);

    /**
     * 从URL提取对象名称
     *
     * @param fileUrl 文件URL
     * @return 对象名称
     */
    String extractObjectNameFromUrl(String fileUrl);

    /**
     * 根据用户当前头像 URL 解析对应的 sys_file 主键（须为本人 AVATAR 记录）
     */
    Long findAvatarFileIdForUser(Long userId, String avatarUrl);

    /**
     * 按存储 URL 查询未删除的 sys_file 记录（用于资料保存后回填 id 等）
     */
    SysFile findByFileUrl(String fileUrl);

    /**
     * 根据文章 ID 与封面 URL 解析 sys_file 主键（须为 ARTICLE_COVER 且 ref_id 为本文章）
     */
    Long findCoverFileIdForArticle(Long articleId, String coverImageUrl);
}
