package com.chen404.service;

import com.chen404.config.SiteRuntimeProperties;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.RemoteArticleImageDownloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** 图片转存的权限、并发限制与文件认领编排；不接受调用方指定上传者或引用类型。 */
@Slf4j
@Service
public class ArticleImageImportService {
    private static final long MAX_DOWNLOAD_BYTES = 12L * 1024 * 1024;
    private final Semaphore downloadSlots = new Semaphore(4);
    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();
    private final AccessService accessService;
    private final SysFileService sysFileService;
    private final SiteRuntimeProperties properties;
    private final RemoteArticleImageDownloader downloader;

    public ArticleImageImportService(AccessService accessService, SysFileService sysFileService,
                                    SiteRuntimeProperties properties, RemoteArticleImageDownloader downloader) {
        this.accessService = accessService;
        this.sysFileService = sysFileService;
        this.properties = properties;
        this.downloader = downloader;
    }

    /** 下载并创建 24 小时临时图片；取消导入时由既有文件清理任务回收。 */
    public UploadFileVO importImage(String url, Long userId) {
        if (!accessService.canCreateArticle(userId)) {
            log.warn("[ARTICLE_IMAGE_IMPORT_DENIED] userId={}", userId);
            throw new ForbiddenException("需要文章创作权限才能转存图片");
        }
        if (!activeUsers.add(userId)) {
            throw new BadRequestException("已有图片正在转存，请稍后重试");
        }
        boolean acquired = downloadSlots.tryAcquire();
        try {
            if (!acquired) {
                throw new BadRequestException("图片转存繁忙，请稍后重试");
            }
            long configuredLimit = properties.getUploadMaxSize();
            long maxBytes = configuredLimit > 0 ? Math.min(configuredLimit, MAX_DOWNLOAD_BYTES) : MAX_DOWNLOAD_BYTES;
            MultipartFile image = downloader.download(url, maxBytes, properties.getUploadAllowTypes());
            SysFile file = sysFileService.uploadTempFile(image, userId, SysFile.RefType.ARTICLE_CONTENT);
            UploadFileVO result = new UploadFileVO();
            result.setId(file.getId());
            result.setUrl(file.getFileUrl());
            result.setName(file.getFileName());
            result.setSize(String.valueOf(file.getFileSize()));
            log.info("[ARTICLE_IMAGE_IMPORT_OK] userId={} fileId={} bytes={}", userId, file.getId(), file.getFileSize());
            return result;
        } catch (IOException exception) {
            // URL 可能含访问签名，异常正文也可能包含 URL，因此只记录异常类型。
            log.warn("[ARTICLE_IMAGE_IMPORT_DOWNLOAD_FAILED] userId={} reason={}", userId, exception.getClass().getSimpleName());
            throw new BadRequestException("图片下载失败或超时，可重试或手动上传");
        } catch (BadRequestException exception) {
            log.warn("[ARTICLE_IMAGE_IMPORT_REJECTED] userId={} reason={}", userId, exception.getMessage());
            throw exception;
        } finally {
            activeUsers.remove(userId);
            if (acquired) {
                downloadSlots.release();
            }
        }
    }
}
