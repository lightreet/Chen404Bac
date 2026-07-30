package com.chen404.service.support.reader;

import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.enums.AdminNotificationResourceTypeEnum;
import com.chen404.domain.event.AdminContentEvent;
import com.chen404.mapper.ReaderBookAssetMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderTocItemMapper;
import com.chen404.service.AdminContentEventPublisher;
import com.chen404.service.FileStorageService;
import com.chen404.service.SysFileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 小说导入成功后的业务事件测试。
 */
class ReaderBookImportProcessorTest {

    @Test
    void shouldPublishAdminNotificationEventAfterBookBecomesReady() throws Exception {
        ReaderBookMapper bookMapper = mock(ReaderBookMapper.class);
        ReaderBookParser parser = mock(ReaderBookParser.class);
        SysFileService sysFileService = mock(SysFileService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        AdminContentEventPublisher eventPublisher = mock(AdminContentEventPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ReaderBook book = new ReaderBook();
        book.setId(42L);
        book.setOwnerUserId(7L);
        book.setTitle("夜航故事");
        book.setSourceFileId(88L);
        book.setStatus(ReaderBook.STATUS_IMPORTING);
        when(bookMapper.selectById(42L)).thenReturn(book);

        SysFile sourceFile = new SysFile();
        sourceFile.setBucketName("reader");
        sourceFile.setObjectName("books/42/source.txt");
        sourceFile.setFileOriginalName("夜航故事.txt");
        when(sysFileService.getById(88L)).thenReturn(sourceFile);
        when(fileStorageService.openFile("reader", "books/42/source.txt"))
                .thenReturn(new ByteArrayInputStream("正文".getBytes()));

        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setTitle("夜航故事");
        parsed.setFormat("txt");
        parsed.setEncoding("UTF-8");
        when(parser.parse(any(), any(), any())).thenReturn(parsed);

        ReaderBookImportProcessor processor = new ReaderBookImportProcessor(
                bookMapper,
                mock(ReaderChapterMapper.class),
                mock(ReaderTocItemMapper.class),
                mock(ReaderBookAssetMapper.class),
                parser,
                sysFileService,
                fileStorageService,
                eventPublisher,
                transactionManager
        );

        processor.process(42L);

        ArgumentCaptor<AdminContentEvent> eventCaptor = ArgumentCaptor.forClass(AdminContentEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        AdminContentEvent event = eventCaptor.getValue();
        assertEquals(AdminNotificationEventTypeEnum.READER_BOOK_IMPORTED, event.eventType());
        assertEquals(AdminNotificationResourceTypeEnum.READER_BOOK, event.resourceType());
        assertEquals(42L, event.resourceId());
        assertEquals(7L, event.actorUserId());
        assertEquals("夜航故事", event.resourceTitle());
    }
}
