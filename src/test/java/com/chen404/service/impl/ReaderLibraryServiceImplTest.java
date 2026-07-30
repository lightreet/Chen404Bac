package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.entity.ReaderBook;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        return new ReaderLibraryServiceImpl(
                bookMapper,
                mock(ReaderChapterMapper.class),
                mock(ReaderTocItemMapper.class),
                mock(ReaderBookAssetMapper.class),
                mock(ReaderProgressMapper.class),
                mock(ReaderPreferenceMapper.class),
                parser,
                mock(SysFileService.class),
                mock(FileReferenceService.class),
                accessService,
                mock(ProtectedFileAccessService.class));
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
