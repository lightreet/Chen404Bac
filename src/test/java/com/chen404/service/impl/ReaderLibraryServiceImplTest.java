package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderBookPreviewVO;
import com.chen404.domain.dto.ReaderBookUpdateCommand;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.enums.ReaderBookVisibilityEnum;
import com.chen404.domain.enums.UserCapabilityEnum;
import com.chen404.exception.ForbiddenException;
import com.chen404.mapper.ReaderBookAssetMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderPreferenceMapper;
import com.chen404.mapper.ReaderProgressMapper;
import com.chen404.mapper.ReaderTocItemMapper;
import com.chen404.service.FileReferenceService;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.service.AccessService;
import com.chen404.service.SysFileService;
import com.chen404.service.support.reader.ReaderBookParser;
import com.chen404.service.support.reader.ParsedReaderBook;
import com.chen404.service.support.reader.ReaderBookImportProcessor;
import com.chen404.service.support.reader.ReaderImportTaskRunner;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 书架公开与私密访问边界测试。
 */
class ReaderLibraryServiceImplTest {

    @Test
    void shouldOnlyQueryPublicBooksForAnonymousViewer() {
        initTableInfo(ReaderBook.class);
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        when(bookMapper.selectList(any())).thenReturn(List.of());

        service(bookMapper).listBooks(null);

        ArgumentCaptor<LambdaQueryWrapper<ReaderBook>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bookMapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().toLowerCase().contains("visibility"));
    }

    @Test
    void shouldAllowAnonymousViewerToOpenPublicBook() {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        when(bookMapper.selectById(7L)).thenReturn(book(7L, 12L, ReaderBookVisibilityEnum.PUBLIC.getCode()));

        ReaderBookVO result = service(bookMapper).getBook(7L, null);

        assertFalse(result.getOwnedByCurrentUser());
        assertTrue(ReaderBookVisibilityEnum.PUBLIC.getCode().equals(result.getVisibility()));
    }

    @Test
    void shouldKeepPrivateBookVisibleOnlyToOwner() {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        when(bookMapper.selectById(8L)).thenReturn(book(8L, 12L, ReaderBookVisibilityEnum.PRIVATE.getCode()));

        assertThrows(ForbiddenException.class, () -> service(bookMapper).getBook(8L, null));

        ReaderBookVO ownerResult = service(bookMapper).getBook(8L, 12L);
        assertTrue(ownerResult.getOwnedByCurrentUser());
    }

    @Test
    void shouldAllowFriendToOpenFriendVisibleBook() {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        AccessService accessService = mock(AccessService.class);
        ReaderBook friendBook = book(9L, 12L, ReaderBookVisibilityEnum.FRIEND.getCode());
        when(bookMapper.selectById(9L)).thenReturn(friendBook);
        when(accessService.hasCapability(18L, UserCapabilityEnum.FRIEND_CONTENT_VIEW.getCode())).thenReturn(true);

        ReaderBookVO result = service(bookMapper, mock(ReaderBookParser.class), accessService).getBook(9L, 18L);

        assertTrue(result.getId().equals(friendBook.getId()));
        assertFalse(result.getOwnedByCurrentUser());
    }

    @Test
    void shouldRejectUpdatingOrDeletingAnotherUsersBook() {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        AccessService accessService = mock(AccessService.class);
        ReaderBook otherUsersBook = book(10L, 12L, ReaderBookVisibilityEnum.PUBLIC.getCode());
        when(bookMapper.selectById(10L)).thenReturn(otherUsersBook);
        when(accessService.canManageReaderBook(18L, otherUsersBook)).thenReturn(false);
        ReaderLibraryServiceImpl service = service(bookMapper, mock(ReaderBookParser.class), accessService);
        ReaderBookUpdateCommand command = new ReaderBookUpdateCommand();
        command.setTitle("越权修改");
        command.setVisibility(ReaderBookVisibilityEnum.PUBLIC.getCode());

        assertThrows(ForbiddenException.class, () -> service.updateBook(10L, command, 18L));
        assertThrows(ForbiddenException.class, () -> service.deleteBook(10L, 18L));
        verify(bookMapper, never()).updateById(any(ReaderBook.class));
        verify(bookMapper, never()).deleteById(10L);
    }

    @Test
    void shouldReuseExistingBookBeforeParsingDuplicateImport() throws Exception {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        ReaderBookParser parser = mock(ReaderBookParser.class);
        MultipartFile file = mock(MultipartFile.class);
        ReaderBook existing = book(11L, 12L, ReaderBookVisibilityEnum.PRIVATE.getCode());
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("重复小说.txt");
        when(file.getBytes()).thenReturn("same-content".getBytes());
        when(parser.sha256(any())).thenReturn("checksum");
        when(bookMapper.selectOne(any())).thenReturn(existing);

        ReaderBookVO result = service(bookMapper, parser).importBook(
                file, null, null, null, null, ReaderBookVisibilityEnum.PRIVATE.getCode(), null, 12L);

        assertTrue(result.getId().equals(existing.getId()));
        verify(parser, never()).parse(any(), any(), any());
    }

    @Test
    void shouldPreviewParsedMetadataAndEmbeddedCover() throws Exception {
        ReaderBookParser parser = mock(ReaderBookParser.class);
        MultipartFile file = mock(MultipartFile.class);
        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setTitle("解析书名");
        parsed.setAuthor("解析作者");
        parsed.setDescription("解析简介");
        parsed.setLanguage("zh");
        parsed.setFormat("epub");
        parsed.setEncoding("UTF-8");
        parsed.getAssets().add(new ParsedReaderBook.Asset(
                "cover.jpg", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3}, true, "asset://cover"));
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("测试.epub");
        when(file.getBytes()).thenReturn(new byte[]{9, 8, 7});
        when(parser.sha256(any())).thenReturn("preview-checksum");
        when(parser.parse(any(), any(), any())).thenReturn(parsed);

        ReaderBookPreviewVO preview = service(mock(ReaderBookMapper.class), parser)
                .previewBook(file, null, 12L);

        assertEquals("解析书名", preview.getTitle());
        assertEquals("解析作者", preview.getAuthor());
        assertEquals("解析简介", preview.getDescription());
        assertTrue(preview.getCoverDataUrl().startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void shouldCreateImportingTaskWithoutParsingInRequestThread() throws Exception {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        ReaderBookParser parser = mock(ReaderBookParser.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileReferenceService fileReferenceService = mock(FileReferenceService.class);
        ReaderImportTaskRunner taskRunner = mock(ReaderImportTaskRunner.class);
        ReaderBookImportProcessor importProcessor = mock(ReaderBookImportProcessor.class);
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("后台小说.txt");
        when(file.getBytes()).thenReturn("chapter content".getBytes(StandardCharsets.UTF_8));
        when(parser.sha256(any())).thenReturn("background-checksum");
        when(parser.detectSourceFormat(any(), any())).thenReturn("txt");
        when(bookMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ReaderBook book = invocation.getArgument(0);
            book.setId(21L);
            book.setCreateTime(LocalDateTime.now());
            book.setUpdateTime(LocalDateTime.now());
            return 1;
        }).when(bookMapper).insert(any(ReaderBook.class));

        SysFile sourceFile = new SysFile();
        sourceFile.setId(31L);
        sourceFile.setFileUrl("/api/files/31");
        when(sysFileService.uploadTempFile(file, 12L, SysFile.RefType.NOVEL_SOURCE)).thenReturn(sourceFile);

        ReaderBookVO result = service(
                bookMapper,
                parser,
                mock(AccessService.class),
                sysFileService,
                fileReferenceService,
                taskRunner,
                importProcessor
        ).importBook(
                file,
                null,
                null,
                null,
                null,
                ReaderBookVisibilityEnum.PRIVATE.getCode(),
                null,
                12L
        );

        assertEquals(ReaderBook.STATUS_IMPORTING, result.getStatus());
        assertEquals(0, result.getChapterCount());
        verify(parser, never()).parse(any(), any(), any());
        verify(taskRunner).runAsync(eq(21L));
    }

    private ReaderLibraryServiceImpl service(ReaderBookMapper bookMapper) {
        return service(bookMapper, mock(ReaderBookParser.class));
    }

    private ReaderLibraryServiceImpl service(ReaderBookMapper bookMapper, ReaderBookParser parser) {
        return service(bookMapper, parser, mock(AccessService.class));
    }

    private ReaderLibraryServiceImpl service(
            ReaderBookMapper bookMapper,
            ReaderBookParser parser,
            AccessService accessService) {
        return service(
                bookMapper,
                parser,
                accessService,
                mock(SysFileService.class),
                mock(FileReferenceService.class),
                mock(ReaderImportTaskRunner.class),
                mock(ReaderBookImportProcessor.class)
        );
    }

    private ReaderLibraryServiceImpl service(
            ReaderBookMapper bookMapper,
            ReaderBookParser parser,
            AccessService accessService,
            SysFileService sysFileService,
            FileReferenceService fileReferenceService,
            ReaderImportTaskRunner taskRunner,
            ReaderBookImportProcessor importProcessor) {
        return new ReaderLibraryServiceImpl(
                bookMapper,
                mock(ReaderChapterMapper.class),
                mock(ReaderTocItemMapper.class),
                mock(ReaderBookAssetMapper.class),
                mock(ReaderProgressMapper.class),
                mock(ReaderPreferenceMapper.class),
                parser,
                sysFileService,
                fileReferenceService,
                accessService,
                mock(ProtectedFileAccessService.class),
                taskRunner,
                importProcessor);
    }

    private ReaderBook book(Long id, Long ownerUserId, String visibility) {
        ReaderBook book = new ReaderBook();
        book.setId(id);
        book.setOwnerUserId(ownerUserId);
        book.setTitle("测试小说");
        book.setVisibility(visibility);
        book.setSourceFormat("txt");
        book.setStatus(ReaderBook.STATUS_READY);
        book.setChapterCount(1);
        book.setTotalCharCount(120L);
        book.setContentVersion(1);
        book.setCreateTime(LocalDateTime.now());
        book.setUpdateTime(LocalDateTime.now());
        return book;
    }

    private void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.remove(entityClass);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "reader-library-test");
        assistant.setCurrentNamespace("reader-library-test");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
