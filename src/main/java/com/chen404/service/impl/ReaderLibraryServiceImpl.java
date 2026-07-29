package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.domain.dto.ReaderBookUpdateCommand;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderChapterVO;
import com.chen404.domain.dto.ReaderPreferenceCommand;
import com.chen404.domain.dto.ReaderPreferenceVO;
import com.chen404.domain.dto.ReaderProgressCommand;
import com.chen404.domain.dto.ReaderProgressVO;
import com.chen404.domain.dto.ReaderSearchResultVO;
import com.chen404.domain.dto.ReaderTocItemVO;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.ReaderBookAsset;
import com.chen404.domain.entity.ReaderChapter;
import com.chen404.domain.entity.ReaderPreference;
import com.chen404.domain.entity.ReaderProgress;
import com.chen404.domain.entity.ReaderTocItem;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.ReaderBookAssetMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderPreferenceMapper;
import com.chen404.mapper.ReaderProgressMapper;
import com.chen404.mapper.ReaderTocItemMapper;
import com.chen404.service.FileClaim;
import com.chen404.service.FileReferenceService;
import com.chen404.service.ReaderLibraryService;
import com.chen404.service.SysFileService;
import com.chen404.service.support.reader.ParsedReaderBook;
import com.chen404.service.support.reader.ReaderBookParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 私人书架实现。
 *
 * <p>原始文件先完成解析校验，再进入受保护文件存储；正文、目录、插图与进度在同一事务内写入。
 * 阅读进度以 MySQL 为跨设备真源，前端本地副本仅用于离线和页面关闭前兜底。</p>
 */
@Slf4j
@Service
public class ReaderLibraryServiceImpl implements ReaderLibraryService {

    private static final int DEFAULT_FONT_SIZE = 18;
    private static final BigDecimal DEFAULT_LINE_HEIGHT = new BigDecimal("1.85");
    private static final int DEFAULT_CONTENT_WIDTH = 720;
    private static final int DEFAULT_PARAGRAPH_SPACING = 16;
    private static final int SEARCH_LIMIT = 50;

    private final ReaderBookMapper bookMapper;
    private final ReaderChapterMapper chapterMapper;
    private final ReaderTocItemMapper tocItemMapper;
    private final ReaderBookAssetMapper assetMapper;
    private final ReaderProgressMapper progressMapper;
    private final ReaderPreferenceMapper preferenceMapper;
    private final ReaderBookParser parser;
    private final SysFileService sysFileService;
    private final FileReferenceService fileReferenceService;

    public ReaderLibraryServiceImpl(
            ReaderBookMapper bookMapper,
            ReaderChapterMapper chapterMapper,
            ReaderTocItemMapper tocItemMapper,
            ReaderBookAssetMapper assetMapper,
            ReaderProgressMapper progressMapper,
            ReaderPreferenceMapper preferenceMapper,
            ReaderBookParser parser,
            SysFileService sysFileService,
            FileReferenceService fileReferenceService) {
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.tocItemMapper = tocItemMapper;
        this.assetMapper = assetMapper;
        this.progressMapper = progressMapper;
        this.preferenceMapper = preferenceMapper;
        this.parser = parser;
        this.sysFileService = sysFileService;
        this.fileReferenceService = fileReferenceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderBookVO importBook(
            MultipartFile file,
            String title,
            String author,
            String encoding,
            Long userId) {
        requireUser(userId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要导入的小说文件");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().strip()
                : "未命名小说.txt";
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("无法读取上传文件");
        }

        String checksum = parser.sha256(bytes);
        Long duplicateCount = bookMapper.selectCount(new LambdaQueryWrapper<ReaderBook>()
                .eq(ReaderBook::getOwnerUserId, userId)
                .eq(ReaderBook::getContentChecksum, checksum));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BadRequestException("这本小说已经在你的书架中");
        }

        ParsedReaderBook parsed = parser.parse(originalName, bytes, encoding);
        if (StringUtils.hasText(title)) {
            parsed.setTitle(title.strip());
        }
        if (StringUtils.hasText(author)) {
            parsed.setAuthor(author.strip());
        }

        SysFile sourceFile = sysFileService.uploadTempFile(file, userId, SysFile.RefType.NOVEL_SOURCE);
        ReaderBook book = new ReaderBook();
        book.setOwnerUserId(userId);
        book.setTitle(limit(parsed.getTitle(), 255));
        book.setAuthor(limit(parsed.getAuthor(), 255));
        book.setDescription(limit(parsed.getDescription(), 4_000));
        book.setLanguage(limit(parsed.getLanguage(), 40));
        book.setSourceFormat(parsed.getFormat());
        book.setSourceEncoding(parsed.getEncoding());
        book.setSourceFileId(sourceFile.getId());
        book.setSourceFileUrl(sourceFile.getFileUrl());
        book.setContentChecksum(checksum);
        book.setStatus(ReaderBook.STATUS_READY);
        book.setParseMessage(limit(parsed.getParseMessage(), 1_000));
        book.setChapterCount(parsed.getChapters().size());
        book.setTotalCharCount(parsed.getChapters().stream()
                .map(ParsedReaderBook.Chapter::getContentText)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum());
        book.setContentVersion(1);
        bookMapper.insert(book);

        Map<String, Long> assetIds = persistAssets(book.getId(), parsed.getAssets());
        List<Long> chapterIds = persistChapters(book.getId(), parsed.getChapters(), parsed.getAssets(), assetIds);
        persistToc(book.getId(), null, 0, parsed.getToc(), chapterIds);

        Long coverAssetId = parsed.getAssets().stream()
                .filter(ParsedReaderBook.Asset::isCover)
                .map(asset -> assetIds.get(asset.getSourcePath()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (coverAssetId != null) {
            book.setCoverAssetId(coverAssetId);
            bookMapper.updateById(book);
        }

        sysFileService.claimPermanentFiles(
                userId,
                List.of(FileClaim.byIdAndUrl(sourceFile.getId(), sourceFile.getFileUrl())),
                SysFile.RefType.NOVEL_SOURCE,
                book.getId()
        );
        fileReferenceService.syncReaderBookReference(book.getId(), sourceFile.getId());
        log.info("[READER_IMPORT] userId={} bookId={} format={} chapters={} assets={} file={}",
                userId, book.getId(), parsed.getFormat(), chapterIds.size(), assetIds.size(), originalName);
        return toBookVO(book, null, null);
    }

    @Override
    public List<ReaderBookVO> listBooks(Long userId) {
        requireUser(userId);
        List<ReaderBook> books = bookMapper.selectList(new LambdaQueryWrapper<ReaderBook>()
                .eq(ReaderBook::getOwnerUserId, userId)
                .orderByDesc(ReaderBook::getUpdateTime)
                .orderByDesc(ReaderBook::getId));
        if (books.isEmpty()) {
            return List.of();
        }
        List<Long> bookIds = books.stream().map(ReaderBook::getId).toList();
        Map<Long, ReaderProgress> progressByBook = new HashMap<>();
        for (ReaderProgress progress : progressMapper.selectList(new LambdaQueryWrapper<ReaderProgress>()
                .eq(ReaderProgress::getUserId, userId)
                .in(ReaderProgress::getBookId, bookIds))) {
            progressByBook.put(progress.getBookId(), progress);
        }
        Map<Long, String> chapterTitles = new HashMap<>();
        List<Long> currentChapterIds = progressByBook.values().stream()
                .map(ReaderProgress::getChapterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!currentChapterIds.isEmpty()) {
            for (ReaderChapter chapter : chapterMapper.selectBatchIds(currentChapterIds)) {
                chapterTitles.put(chapter.getId(), chapter.getTitle());
            }
        }
        return books.stream()
                .map(book -> toBookVO(
                        book,
                        progressByBook.get(book.getId()),
                        chapterTitles.get(progressByBook.get(book.getId()) == null
                                ? null
                                : progressByBook.get(book.getId()).getChapterId())
                ))
                .sorted((left, right) -> {
                    LocalDateTime leftTime = left.getLastReadAt();
                    LocalDateTime rightTime = right.getLastReadAt();
                    if (leftTime == null && rightTime == null) return 0;
                    if (leftTime == null) return 1;
                    if (rightTime == null) return -1;
                    return rightTime.compareTo(leftTime);
                })
                .toList();
    }

    @Override
    public ReaderBookVO getBook(Long bookId, Long userId) {
        ReaderBook book = requireOwnedBook(bookId, userId);
        ReaderProgress progress = findProgress(bookId, userId);
        ReaderChapter current = progress == null || progress.getChapterId() == null
                ? null
                : chapterMapper.selectById(progress.getChapterId());
        return toBookVO(book, progress, current == null ? null : current.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderBookVO updateBook(Long bookId, ReaderBookUpdateCommand command, Long userId) {
        ReaderBook book = requireOwnedBook(bookId, userId);
        book.setTitle(command.getTitle().strip());
        book.setAuthor(blankToNull(command.getAuthor()));
        book.setDescription(blankToNull(command.getDescription()));
        bookMapper.updateById(book);
        return getBook(bookId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBook(Long bookId, Long userId) {
        ReaderBook book = requireOwnedBook(bookId, userId);
        progressMapper.delete(new LambdaQueryWrapper<ReaderProgress>().eq(ReaderProgress::getBookId, bookId));
        tocItemMapper.delete(new LambdaQueryWrapper<ReaderTocItem>().eq(ReaderTocItem::getBookId, bookId));
        chapterMapper.delete(new LambdaQueryWrapper<ReaderChapter>().eq(ReaderChapter::getBookId, bookId));
        assetMapper.delete(new LambdaQueryWrapper<ReaderBookAsset>().eq(ReaderBookAsset::getBookId, bookId));
        fileReferenceService.removeByOwner(
                com.chen404.domain.entity.FileReference.ModuleCode.READER_BOOK,
                com.chen404.domain.entity.FileReference.BizType.READER_BOOK_SOURCE,
                bookId
        );
        bookMapper.deleteById(bookId);
        if (StringUtils.hasText(book.getSourceFileUrl())) {
            sysFileService.deleteByUrl(book.getSourceFileUrl(), userId);
        }
        log.info("[READER_DELETE] userId={} bookId={}", userId, bookId);
    }

    @Override
    public List<ReaderTocItemVO> getToc(Long bookId, Long userId) {
        requireOwnedBook(bookId, userId);
        List<ReaderTocItem> items = tocItemMapper.selectList(new LambdaQueryWrapper<ReaderTocItem>()
                .eq(ReaderTocItem::getBookId, bookId)
                .orderByAsc(ReaderTocItem::getDepth)
                .orderByAsc(ReaderTocItem::getItemOrder)
                .orderByAsc(ReaderTocItem::getId));
        Map<Long, ReaderTocItemVO> voById = new LinkedHashMap<>();
        List<ReaderTocItemVO> roots = new ArrayList<>();
        for (ReaderTocItem item : items) {
            ReaderTocItemVO vo = new ReaderTocItemVO();
            vo.setId(item.getId());
            vo.setLabel(item.getLabel());
            vo.setChapterId(item.getChapterId());
            vo.setFragment(item.getFragment());
            vo.setDepth(item.getDepth());
            voById.put(item.getId(), vo);
            ReaderTocItemVO parent = item.getParentId() == null ? null : voById.get(item.getParentId());
            if (parent == null) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }

    @Override
    public ReaderChapterVO getChapter(Long bookId, Long chapterId, Long userId) {
        ReaderBook book = requireOwnedBook(bookId, userId);
        ReaderChapter chapter = requireChapter(bookId, chapterId);
        ReaderChapter previous = chapterMapper.selectOne(new LambdaQueryWrapper<ReaderChapter>()
                .eq(ReaderChapter::getBookId, bookId)
                .lt(ReaderChapter::getChapterOrder, chapter.getChapterOrder())
                .orderByDesc(ReaderChapter::getChapterOrder)
                .last("LIMIT 1"));
        ReaderChapter next = chapterMapper.selectOne(new LambdaQueryWrapper<ReaderChapter>()
                .eq(ReaderChapter::getBookId, bookId)
                .gt(ReaderChapter::getChapterOrder, chapter.getChapterOrder())
                .orderByAsc(ReaderChapter::getChapterOrder)
                .last("LIMIT 1"));
        ReaderChapterVO vo = new ReaderChapterVO();
        vo.setId(chapter.getId());
        vo.setBookId(bookId);
        vo.setBookTitle(book.getTitle());
        vo.setChapterOrder(chapter.getChapterOrder());
        vo.setChapterCount(book.getChapterCount());
        vo.setTitle(chapter.getTitle());
        vo.setVolumeTitle(chapter.getVolumeTitle());
        vo.setContentHtml(chapter.getContentHtml());
        vo.setCharCount(chapter.getCharCount());
        vo.setPreviousChapterId(previous == null ? null : previous.getId());
        vo.setNextChapterId(next == null ? null : next.getId());
        return vo;
    }

    @Override
    public List<ReaderSearchResultVO> search(Long bookId, String keyword, Long userId) {
        requireOwnedBook(bookId, userId);
        String query = keyword == null ? "" : keyword.strip();
        if (query.length() < 2) {
            throw new BadRequestException("搜索关键词至少需要 2 个字符");
        }
        if (query.length() > 50) {
            throw new BadRequestException("搜索关键词不能超过 50 个字符");
        }
        List<ReaderChapter> matches = chapterMapper.selectList(new LambdaQueryWrapper<ReaderChapter>()
                .eq(ReaderChapter::getBookId, bookId)
                .and(wrapper -> wrapper.like(ReaderChapter::getTitle, query)
                        .or()
                        .like(ReaderChapter::getContentText, query))
                .orderByAsc(ReaderChapter::getChapterOrder)
                .last("LIMIT " + SEARCH_LIMIT));
        return matches.stream()
                .map(chapter -> new ReaderSearchResultVO(
                        chapter.getId(),
                        chapter.getTitle(),
                        chapter.getChapterOrder(),
                        buildSnippet(chapter.getContentText(), query)
                ))
                .toList();
    }

    @Override
    public ReaderProgressVO getProgress(Long bookId, Long userId) {
        requireOwnedBook(bookId, userId);
        ReaderProgress progress = findProgress(bookId, userId);
        return progress == null ? null : toProgressVO(progress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderProgressVO saveProgress(Long bookId, ReaderProgressCommand command, Long userId) {
        ReaderBook book = requireOwnedBook(bookId, userId);
        ReaderChapter chapter = requireChapter(bookId, command.getChapterId());
        ReaderProgress progress = findProgress(bookId, userId);
        if (progress == null) {
            progress = new ReaderProgress();
            progress.setUserId(userId);
            progress.setBookId(bookId);
        }
        progress.setChapterId(chapter.getId());
        progress.setBlockIndex(Math.max(0, command.getBlockIndex()));
        progress.setCharacterOffset(Math.max(0, command.getCharacterOffset()));
        progress.setProgressPercent(command.getProgressPercent().setScale(3, RoundingMode.HALF_UP));
        progress.setLocatorContext(limit(blankToNull(command.getLocatorContext()), 255));
        progress.setContentVersion(book.getContentVersion());
        progress.setFinished(Boolean.TRUE.equals(command.getFinished())
                || command.getProgressPercent().compareTo(new BigDecimal("99.9")) >= 0);
        progress.setLastReadAt(LocalDateTime.now());
        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
        return toProgressVO(progress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearProgress(Long bookId, Long userId) {
        requireOwnedBook(bookId, userId);
        progressMapper.delete(new LambdaQueryWrapper<ReaderProgress>()
                .eq(ReaderProgress::getBookId, bookId)
                .eq(ReaderProgress::getUserId, userId));
    }

    @Override
    public ReaderPreferenceVO getPreference(Long userId) {
        requireUser(userId);
        ReaderPreference preference = preferenceMapper.selectOne(new LambdaQueryWrapper<ReaderPreference>()
                .eq(ReaderPreference::getUserId, userId)
                .last("LIMIT 1"));
        return toPreferenceVO(preference == null ? defaultPreference(userId) : preference);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderPreferenceVO savePreference(ReaderPreferenceCommand command, Long userId) {
        requireUser(userId);
        ReaderPreference preference = preferenceMapper.selectOne(new LambdaQueryWrapper<ReaderPreference>()
                .eq(ReaderPreference::getUserId, userId)
                .last("LIMIT 1"));
        if (preference == null) {
            preference = defaultPreference(userId);
        }
        if (command.getFontSize() != null) preference.setFontSize(command.getFontSize());
        if (command.getLineHeight() != null) preference.setLineHeight(command.getLineHeight());
        if (command.getContentWidth() != null) preference.setContentWidth(command.getContentWidth());
        if (command.getParagraphSpacing() != null) preference.setParagraphSpacing(command.getParagraphSpacing());
        if (StringUtils.hasText(command.getTheme())) preference.setTheme(command.getTheme());
        if (StringUtils.hasText(command.getFontFamily())) preference.setFontFamily(command.getFontFamily());
        if (preference.getId() == null) {
            preferenceMapper.insert(preference);
        } else {
            preferenceMapper.updateById(preference);
        }
        return toPreferenceVO(preference);
    }

    @Override
    public ReaderAssetPayload getAsset(Long bookId, Long assetId, Long userId) {
        requireOwnedBook(bookId, userId);
        ReaderBookAsset asset = assetMapper.selectById(assetId);
        if (asset == null || !Objects.equals(asset.getBookId(), bookId)) {
            throw new ResourceNotFoundException("书籍资源不存在");
        }
        return new ReaderAssetPayload(asset.getFileName(), asset.getMediaType(), asset.getAssetData());
    }

    private Map<String, Long> persistAssets(Long bookId, List<ParsedReaderBook.Asset> assets) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (ParsedReaderBook.Asset parsed : assets) {
            ReaderBookAsset asset = new ReaderBookAsset();
            asset.setBookId(bookId);
            asset.setSourcePath(limit(parsed.getSourcePath(), 1_000));
            asset.setSourcePathHash(parser.sha256(parsed.getSourcePath()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
            chapter.setContentHash(parser.sha256(parsed.getContentText()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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

    private ReaderBook requireOwnedBook(Long bookId, Long userId) {
        requireUser(userId);
        ReaderBook book = bookId == null ? null : bookMapper.selectById(bookId);
        if (book == null) {
            throw new ResourceNotFoundException("小说不存在");
        }
        if (!Objects.equals(book.getOwnerUserId(), userId)) {
            throw new ForbiddenException("只能访问自己书架中的小说");
        }
        return book;
    }

    private ReaderChapter requireChapter(Long bookId, Long chapterId) {
        ReaderChapter chapter = chapterId == null ? null : chapterMapper.selectById(chapterId);
        if (chapter == null || !Objects.equals(chapter.getBookId(), bookId)) {
            throw new ResourceNotFoundException("章节不存在");
        }
        return chapter;
    }

    private ReaderProgress findProgress(Long bookId, Long userId) {
        return progressMapper.selectOne(new LambdaQueryWrapper<ReaderProgress>()
                .eq(ReaderProgress::getBookId, bookId)
                .eq(ReaderProgress::getUserId, userId)
                .last("LIMIT 1"));
    }

    private ReaderBookVO toBookVO(ReaderBook book, ReaderProgress progress, String currentChapterTitle) {
        ReaderBookVO vo = new ReaderBookVO();
        vo.setId(book.getId());
        vo.setTitle(book.getTitle());
        vo.setAuthor(book.getAuthor());
        vo.setDescription(book.getDescription());
        vo.setLanguage(book.getLanguage());
        vo.setSourceFormat(book.getSourceFormat());
        vo.setSourceEncoding(book.getSourceEncoding());
        vo.setStatus(book.getStatus());
        vo.setParseMessage(book.getParseMessage());
        vo.setChapterCount(book.getChapterCount());
        vo.setTotalCharCount(book.getTotalCharCount());
        vo.setContentVersion(book.getContentVersion());
        vo.setCoverUrl(book.getCoverAssetId() == null
                ? null
                : "/api/reader/books/" + book.getId() + "/assets/" + book.getCoverAssetId());
        vo.setCurrentChapterId(progress == null ? null : progress.getChapterId());
        vo.setCurrentChapterTitle(currentChapterTitle);
        vo.setProgressPercent(progress == null ? BigDecimal.ZERO : progress.getProgressPercent());
        vo.setFinished(progress != null && Boolean.TRUE.equals(progress.getFinished()));
        vo.setLastReadAt(progress == null ? null : progress.getLastReadAt());
        vo.setCreateTime(book.getCreateTime());
        vo.setUpdateTime(book.getUpdateTime());
        return vo;
    }

    private ReaderProgressVO toProgressVO(ReaderProgress progress) {
        ReaderProgressVO vo = new ReaderProgressVO();
        vo.setBookId(progress.getBookId());
        vo.setChapterId(progress.getChapterId());
        vo.setBlockIndex(progress.getBlockIndex());
        vo.setCharacterOffset(progress.getCharacterOffset());
        vo.setProgressPercent(progress.getProgressPercent());
        vo.setLocatorContext(progress.getLocatorContext());
        vo.setContentVersion(progress.getContentVersion());
        vo.setFinished(progress.getFinished());
        vo.setLastReadAt(progress.getLastReadAt());
        return vo;
    }

    private ReaderPreference defaultPreference(Long userId) {
        ReaderPreference preference = new ReaderPreference();
        preference.setUserId(userId);
        preference.setFontSize(DEFAULT_FONT_SIZE);
        preference.setLineHeight(DEFAULT_LINE_HEIGHT);
        preference.setContentWidth(DEFAULT_CONTENT_WIDTH);
        preference.setParagraphSpacing(DEFAULT_PARAGRAPH_SPACING);
        preference.setTheme("light");
        preference.setFontFamily("serif");
        return preference;
    }

    private ReaderPreferenceVO toPreferenceVO(ReaderPreference preference) {
        ReaderPreferenceVO vo = new ReaderPreferenceVO();
        vo.setFontSize(preference.getFontSize());
        vo.setLineHeight(preference.getLineHeight());
        vo.setContentWidth(preference.getContentWidth());
        vo.setParagraphSpacing(preference.getParagraphSpacing());
        vo.setTheme(preference.getTheme());
        vo.setFontFamily(preference.getFontFamily());
        return vo;
    }

    private String buildSnippet(String text, String keyword) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int index = text.toLowerCase().indexOf(keyword.toLowerCase());
        if (index < 0) {
            return limit(text, 120);
        }
        int start = Math.max(0, index - 45);
        int end = Math.min(text.length(), index + keyword.length() + 75);
        return (start > 0 ? "…" : "") + text.substring(start, end).strip() + (end < text.length() ? "…" : "");
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ForbiddenException("请先登录后再使用书架");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
