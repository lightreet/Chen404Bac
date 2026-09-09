package com.chen404.service.support.reader;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chen404.config.ReaderImportProperties;
import com.chen404.config.ReaderImportTaskConfig;
import com.chen404.domain.ReaderBookConstraints;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.ReaderBookAsset;
import com.chen404.domain.entity.ReaderChapter;
import com.chen404.domain.entity.ReaderTocItem;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.enums.AdminNotificationResourceTypeEnum;
import com.chen404.domain.event.AdminContentEvent;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.ReaderBookAssetMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderTocItemMapper;
import com.chen404.service.AdminContentEventPublisher;
import com.chen404.service.FileStorageService;
import com.chen404.service.SysFileService;
import com.chen404.util.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 小说后台解析与持久化处理器。
 *
 * <p>原文件读取和正文解析在事务外完成，章节、目录和资源在一个短事务中整体写入。
 * 写入前验证租约；失败回滚后只允许当前执行器登记重试或终止状态。</p>
 */
@Slf4j
@Service
public class ReaderBookImportProcessor {

    private final ReaderBookMapper bookMapper;
    private final ReaderChapterMapper chapterMapper;
    private final ReaderTocItemMapper tocItemMapper;
    private final ReaderBookAssetMapper assetMapper;
    private final ReaderBookParser parser;
    private final SysFileService sysFileService;
    private final FileStorageService fileStorageService;
    private final AdminContentEventPublisher adminContentEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ReaderImportProperties properties;
    private final ScheduledExecutorService scheduler;

    public ReaderBookImportProcessor(
            ReaderBookMapper bookMapper,
            ReaderChapterMapper chapterMapper,
            ReaderTocItemMapper tocItemMapper,
            ReaderBookAssetMapper assetMapper,
            ReaderBookParser parser,
            SysFileService sysFileService,
            FileStorageService fileStorageService,
            AdminContentEventPublisher adminContentEventPublisher,
            PlatformTransactionManager transactionManager,
            ReaderImportProperties properties,
            @Qualifier(ReaderImportTaskConfig.READER_IMPORT_LEASE_SCHEDULER) ScheduledExecutorService scheduler) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.tocItemMapper = tocItemMapper;
        this.assetMapper = assetMapper;
        this.parser = parser;
        this.sysFileService = sysFileService;
        this.fileStorageService = fileStorageService;
        this.adminContentEventPublisher = adminContentEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
        this.scheduler = scheduler;
    }

    /**
     * 执行一次可重复调度的导入任务。
     *
     * @param bookId 任务对应的书籍 ID
     */
    public void process(Long bookId) {
        String token = UUID.randomUUID().toString();
        ReaderBook task = transactionTemplate.execute(status -> {
            if (bookMapper.tryClaimImport(bookId, token, properties.getLeaseSeconds()) != 1) {
                return null;
            }
            return bookMapper.selectById(bookId);
        });
        if (task == null) {
            return;
        }
        if (task.getImportAttempts() > properties.getMaxAttempts()) {
            recordFailure(bookId, token, new BadRequestException("导入多次中断，请重新导入"));
            return;
        }
        log.info("[READER_IMPORT_CLAIMED] bookId={} attempt={}", bookId, task.getImportAttempts());
        ScheduledFuture<?> heartbeat = null;
        try {
            int interval = Math.max(1, properties.getLeaseSeconds() / 3);
            heartbeat = scheduler.scheduleWithFixedDelay(() -> renewLease(bookId, token),
                    interval, interval, TimeUnit.SECONDS);
            long startedAt = System.nanoTime();
            ParsedReaderBook parsed = parseStoredSource(task);
            long parsedAt = System.nanoTime();
            PersistSummary summary = transactionTemplate.execute(status -> persistParsedBook(bookId, token, parsed));
            if (summary == null) {
                log.info("[READER_IMPORT_LEASE_LOST] bookId={}", bookId);
                return;
            }
            log.info("[READER_IMPORT_OK] userId={} bookId={} format={} chapters={} assets={} parseMs={} persistMs={}",
                    task.getOwnerUserId(), bookId, parsed.getFormat(), summary.chapterCount(), summary.assetCount(),
                    elapsedMillis(startedAt, parsedAt), elapsedMillis(parsedAt, System.nanoTime()));
        } catch (RuntimeException exception) {
            recordFailure(bookId, token, exception);
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    private void renewLease(Long bookId, String token) {
        try {
            bookMapper.renewImportLease(bookId, token, properties.getLeaseSeconds());
        } catch (RuntimeException exception) {
            log.warn("[READER_IMPORT_RENEW_FAIL] bookId={}", bookId, exception);
        }
    }

    private void recordFailure(Long bookId, String token, RuntimeException exception) {
        boolean terminal = exception instanceof BadRequestException;
        String message = terminal ? exception.getMessage() : "后台导入失败";
        log.warn("[READER_IMPORT_ATTEMPT_FAIL] bookId={} terminal={}", bookId, terminal, exception);
        transactionTemplate.executeWithoutResult(status -> bookMapper.retryOrFailImport(
                bookId, token, TextUtil.truncate(message, ReaderBookConstraints.PARSE_MESSAGE_MAX_LENGTH), terminal,
                properties.getMaxAttempts(), properties.getRetryDelaySeconds()));
    }

    private ParsedReaderBook parseStoredSource(ReaderBook task) {
        SysFile sourceFile = sysFileService.findById(task.getSourceFileId());
        if (sourceFile == null
                || !StringUtils.hasText(sourceFile.getBucketName())
                || !StringUtils.hasText(sourceFile.getObjectName())) {
            throw new BadRequestException("小说源文件不存在，请删除后重新导入");
        }
        String originalName = StringUtils.hasText(sourceFile.getFileOriginalName())
                ? sourceFile.getFileOriginalName()
                : sourceFile.getFileName();
        try (InputStream input = fileStorageService.openFile(
                sourceFile.getBucketName(),
                sourceFile.getObjectName())) {
            byte[] bytes = input.readNBytes(ReaderBookParser.MAX_SOURCE_SIZE_BYTES + 1);
            if (bytes.length > ReaderBookParser.MAX_SOURCE_SIZE_BYTES) {
                throw new BadRequestException("小说文件不能超过 60MB");
            }
            return parser.parse(originalName, bytes, task.getSourceEncoding());
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取小说源文件", exception);
        }
    }

    private PersistSummary persistParsedBook(Long bookId, String token, ParsedReaderBook parsed) {
        ReaderBook book = bookMapper.selectClaimForUpdate(bookId, token);
        if (book == null || !ReaderBook.STATUS_IMPORTING.equals(book.getStatus())) {
            return null;
        }

        Map<String, Long> assetIds = persistAssets(bookId, parsed.getAssets());
        List<Long> chapterIds = persistChapters(bookId, parsed.getChapters(), parsed.getAssets(), assetIds);
        persistToc(bookId, null, 0, parsed.getToc(), chapterIds);

        Long coverAssetId = parsed.getAssets().stream()
                .filter(ParsedReaderBook.Asset::isCover)
                .map(asset -> assetIds.get(asset.getSourcePath()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        book.setTitle(TextUtil.truncate(firstNonBlank(book.getTitle(), parsed.getTitle()), ReaderBookConstraints.TITLE_MAX_LENGTH));
        book.setAuthor(TextUtil.truncate(firstNonBlank(book.getAuthor(), parsed.getAuthor()), ReaderBookConstraints.AUTHOR_MAX_LENGTH));
        book.setDescription(TextUtil.truncate(firstNonBlank(book.getDescription(), parsed.getDescription()), ReaderBookConstraints.DESCRIPTION_MAX_LENGTH));
        book.setLanguage(TextUtil.truncate(parsed.getLanguage(), ReaderBookConstraints.LANGUAGE_MAX_LENGTH));
        book.setSourceFormat(parsed.getFormat());
        book.setSourceEncoding(parsed.getEncoding());
        book.setStatus(ReaderBook.STATUS_READY);
        book.setParseMessage(TextUtil.truncate(parsed.getParseMessage(), ReaderBookConstraints.PARSE_MESSAGE_MAX_LENGTH));
        book.setChapterCount(chapterIds.size());
        book.setTotalCharCount(parsed.getChapters().stream()
                .map(ParsedReaderBook.Chapter::getContentText)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum());
        book.setCoverAssetId(coverAssetId);
        bookMapper.updateById(book);
        // updateById 默认跳过 null，成功重试必须显式清除旧失败说明及执行租约。
        bookMapper.update(null, new LambdaUpdateWrapper<ReaderBook>()
                .eq(ReaderBook::getId, bookId)
                .set(ReaderBook::getParseMessage, book.getParseMessage())
                .set(ReaderBook::getImportToken, null)
                .set(ReaderBook::getImportLeaseUntil, null));
        adminContentEventPublisher.publish(new AdminContentEvent(
                AdminNotificationEventTypeEnum.READER_BOOK_IMPORTED,
                book.getOwnerUserId(),
                AdminNotificationResourceTypeEnum.READER_BOOK,
                book.getId(),
                book.getTitle()
        ));
        return new PersistSummary(chapterIds.size(), assetIds.size());
    }

    private Map<String, Long> persistAssets(Long bookId, List<ParsedReaderBook.Asset> assets) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (ParsedReaderBook.Asset parsed : assets) {
            ReaderBookAsset asset = new ReaderBookAsset();
            asset.setBookId(bookId);
            asset.setSourcePath(TextUtil.truncate(parsed.getSourcePath(), ReaderBookConstraints.SOURCE_PATH_MAX_LENGTH));
            asset.setSourcePathHash(parser.sha256(parsed.getSourcePath().getBytes(StandardCharsets.UTF_8)));
            asset.setFileName(TextUtil.truncate(parsed.getFileName(), 255));
            asset.setMediaType(TextUtil.truncate(parsed.getMediaType(), 120));
            asset.setFileSize((long) parsed.getData().length);
            asset.setContentHash(parser.sha256(parsed.getData()));
            asset.setAssetData(parsed.getData());
            asset.setIsCover(parsed.isCover());
            asset.setCreateTime(LocalDateTime.now());
            assetMapper.insert(asset);
            ids.put(parsed.getSourcePath(), asset.getId());
        }
        return ids;
    }

    private List<Long> persistChapters(
            Long bookId,
            List<ParsedReaderBook.Chapter> chapters,
            List<ParsedReaderBook.Asset> assets,
            Map<String, Long> assetIds) {
        List<Long> chapterIds = new ArrayList<>();
        for (int index = 0; index < chapters.size(); index++) {
            ParsedReaderBook.Chapter parsed = chapters.get(index);
            String html = parsed.getContentHtml();
            for (ParsedReaderBook.Asset asset : assets) {
                Long assetId = assetIds.get(asset.getSourcePath());
                if (assetId != null && StringUtils.hasText(asset.getPlaceholder())) {
                    html = html.replace(
                            asset.getPlaceholder(),
                            "/api/reader/books/" + bookId + "/assets/" + assetId
                    );
                }
            }
            ReaderChapter chapter = new ReaderChapter();
            chapter.setBookId(bookId);
            chapter.setChapterOrder(index);
            chapter.setTitle(TextUtil.truncate(parsed.getTitle(), ReaderBookConstraints.HEADING_MAX_LENGTH));
            chapter.setVolumeTitle(TextUtil.truncate(parsed.getVolumeTitle(), ReaderBookConstraints.HEADING_MAX_LENGTH));
            chapter.setSourceHref(TextUtil.truncate(parsed.getSourceHref(), ReaderBookConstraints.SOURCE_PATH_MAX_LENGTH));
            chapter.setContentHtml(html);
            chapter.setContentText(parsed.getContentText());
            chapter.setCharCount(parsed.getContentText().length());
            chapter.setContentHash(parser.sha256(parsed.getContentText().getBytes(StandardCharsets.UTF_8)));
            chapterMapper.insert(chapter);
            chapterIds.add(chapter.getId());
        }
        return chapterIds;
    }

    private void persistToc(
            Long bookId,
            Long parentId,
            int depth,
            List<ParsedReaderBook.TocNode> nodes,
            List<Long> chapterIds) {
        for (int index = 0; index < nodes.size(); index++) {
            ParsedReaderBook.TocNode parsed = nodes.get(index);
            ReaderTocItem item = new ReaderTocItem();
            item.setBookId(bookId);
            item.setParentId(parentId);
            item.setChapterId(validChapterId(parsed.getChapterIndex(), chapterIds));
            item.setItemOrder(index);
            item.setDepth(depth);
            item.setLabel(TextUtil.truncate(parsed.getLabel(), ReaderBookConstraints.HEADING_MAX_LENGTH));
            item.setSourceHref(TextUtil.truncate(parsed.getSourceHref(), ReaderBookConstraints.SOURCE_PATH_MAX_LENGTH));
            item.setFragment(TextUtil.truncate(parsed.getFragment(), 500));
            item.setCreateTime(LocalDateTime.now());
            tocItemMapper.insert(item);
            persistToc(bookId, item.getId(), depth + 1, parsed.getChildren(), chapterIds);
        }
    }

    private Long validChapterId(Integer chapterIndex, List<Long> chapterIds) {
        return chapterIndex != null && chapterIndex >= 0 && chapterIndex < chapterIds.size()
                ? chapterIds.get(chapterIndex)
                : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.strip() : fallback;
    }

    private long elapsedMillis(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000;
    }


    private record PersistSummary(int chapterCount, int assetCount) {
    }
}
