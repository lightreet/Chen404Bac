package com.chen404.service.support.reader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * 小说后台解析与持久化处理器。
 *
 * <p>原文件读取和正文解析在事务外完成，章节、目录和资源在一个短事务中整体写入。
 * 任一步失败都会回滚内容数据，由任务执行器另行把书籍状态标记为失败。</p>
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

    public ReaderBookImportProcessor(
            ReaderBookMapper bookMapper,
            ReaderChapterMapper chapterMapper,
            ReaderTocItemMapper tocItemMapper,
            ReaderBookAssetMapper assetMapper,
            ReaderBookParser parser,
            SysFileService sysFileService,
            FileStorageService fileStorageService,
            AdminContentEventPublisher adminContentEventPublisher,
            PlatformTransactionManager transactionManager) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.tocItemMapper = tocItemMapper;
        this.assetMapper = assetMapper;
        this.parser = parser;
        this.sysFileService = sysFileService;
        this.fileStorageService = fileStorageService;
        this.adminContentEventPublisher = adminContentEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 执行一次可重复调度的导入任务。
     *
     * @param bookId 任务对应的书籍 ID
     */
    public void process(Long bookId) {
        ReaderBook task = bookMapper.selectById(bookId);
        if (task == null || !ReaderBook.STATUS_IMPORTING.equals(task.getStatus())) {
            log.info("[READER_IMPORT_SKIPPED] bookId={} reason=task-not-importing", bookId);
            return;
        }

        long startedAt = System.nanoTime();
        ParsedReaderBook parsed = parseStoredSource(task);
        long parsedAt = System.nanoTime();

        PersistSummary summary = transactionTemplate.execute(status -> persistParsedBook(bookId, parsed));
        if (summary == null) {
            log.info("[READER_IMPORT_SKIPPED] bookId={} reason=task-state-changed", bookId);
            return;
        }
        log.info(
                "[READER_IMPORT_OK] userId={} bookId={} format={} chapters={} assets={} parseMs={} persistMs={}",
                task.getOwnerUserId(),
                bookId,
                parsed.getFormat(),
                summary.chapterCount(),
                summary.assetCount(),
                elapsedMillis(startedAt, parsedAt),
                elapsedMillis(parsedAt, System.nanoTime())
        );
    }

    /**
     * 在独立事务中记录失败状态，确保正文持久化事务回滚后用户仍能看到明确结果。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long bookId, String message) {
        ReaderBook book = bookMapper.selectById(bookId);
        if (book == null || !ReaderBook.STATUS_IMPORTING.equals(book.getStatus())) {
            return;
        }
        book.setStatus(ReaderBook.STATUS_FAILED);
        book.setParseMessage(limit(message, 1_000));
        bookMapper.updateById(book);
    }

    private ParsedReaderBook parseStoredSource(ReaderBook task) {
        SysFile sourceFile = sysFileService.getById(task.getSourceFileId());
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
            throw new BadRequestException("无法读取小说源文件");
        }
    }

    private PersistSummary persistParsedBook(Long bookId, ParsedReaderBook parsed) {
        ReaderBook book = bookMapper.selectById(bookId);
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

        book.setTitle(limit(firstNonBlank(book.getTitle(), parsed.getTitle()), 255));
        book.setAuthor(limit(firstNonBlank(book.getAuthor(), parsed.getAuthor()), 255));
        book.setDescription(limit(firstNonBlank(book.getDescription(), parsed.getDescription()), 4_000));
        book.setLanguage(limit(parsed.getLanguage(), 40));
        book.setSourceFormat(parsed.getFormat());
        book.setSourceEncoding(parsed.getEncoding());
        book.setStatus(ReaderBook.STATUS_READY);
        book.setParseMessage(limit(parsed.getParseMessage(), 1_000));
        book.setChapterCount(chapterIds.size());
        book.setTotalCharCount(parsed.getChapters().stream()
                .map(ParsedReaderBook.Chapter::getContentText)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum());
        book.setCoverAssetId(coverAssetId);
        bookMapper.updateById(book);
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
            asset.setSourcePath(limit(parsed.getSourcePath(), 1_000));
            asset.setSourcePathHash(parser.sha256(parsed.getSourcePath().getBytes(StandardCharsets.UTF_8)));
            asset.setFileName(limit(parsed.getFileName(), 255));
            asset.setMediaType(limit(parsed.getMediaType(), 120));
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
            chapter.setTitle(limit(parsed.getTitle(), 500));
            chapter.setVolumeTitle(limit(parsed.getVolumeTitle(), 500));
            chapter.setSourceHref(limit(parsed.getSourceHref(), 1_000));
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
            item.setLabel(limit(parsed.getLabel(), 500));
            item.setSourceHref(limit(parsed.getSourceHref(), 1_000));
            item.setFragment(limit(parsed.getFragment(), 500));
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

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private record PersistSummary(int chapterCount, int assetCount) {
    }
}
