package com.chen404.service.support.reader;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chen404.config.ReaderImportProperties;
import com.chen404.config.MyBatisMetaObjectHandler;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.SysFile;
import com.chen404.mapper.ReaderBookAssetMapper;
import com.chen404.mapper.ReaderBookMapper;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderTocItemMapper;
import com.chen404.service.AdminContentEventPublisher;
import com.chen404.service.FileStorageService;
import com.chen404.service.SysFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 实际执行事务和 Mapper，验证恢复导入的成功清理、落库回滚及旧执行器隔离。 */
class ReaderBookImportTransactionTest {
    private JdbcTemplate jdbc;
    private ReaderBookMapper books;
    private ReaderBookImportProcessor processor;
    private ReaderBookParser parser;
    private AdminContentEventPublisher events;
    private ParsedReaderBook parsed;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:reader-import-tx;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2026072901__create_reader_library.sql"),
                new ClassPathResource("db/migration/V2026090901__add_reader_import_lease.sql")).execute(source);
        jdbc.execute("ALTER TABLE reader_book ADD COLUMN visibility VARCHAR(20) DEFAULT 'private'");
        jdbc.execute("ALTER TABLE reader_book ADD COLUMN cover_file_id BIGINT");
        jdbc.update("INSERT INTO reader_book (id,owner_user_id,title,source_format,source_file_id,content_checksum,status) "
                + "VALUES (1,7,'book','txt',88,'checksum','importing')");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        // Mapper 注册时会初始化表元数据，必须先提供与应用一致的审计字段填充器。
        var globalConfig = new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig())
                .setMetaObjectHandler(new MyBatisMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.addMapper(ReaderBookMapper.class);
        configuration.addMapper(ReaderChapterMapper.class);
        configuration.addMapper(ReaderBookAssetMapper.class);
        configuration.addMapper(ReaderTocItemMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(globalConfig);
        factory.setMapperLocations(new ClassPathResource("mapper/ReaderBookMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        books = session.getMapper(ReaderBookMapper.class);
        parser = spy(new ReaderBookParser());
        parsed = new ParsedReaderBook();
        parsed.setTitle("book");
        parsed.setFormat("txt");
        parsed.setEncoding("UTF-8");
        parsed.setChapters(List.of(new ParsedReaderBook.Chapter("chapter", null, "chapter.txt", "<p>body</p>", "body")));
        parsed.setAssets(List.of(new ParsedReaderBook.Asset("cover.png", "cover.png", "image/png",
                new byte[]{1, 2}, true, "cover-placeholder")));
        parsed.setToc(List.of(new ParsedReaderBook.TocNode("chapter", 0)));
        doReturn(parsed).when(parser).parse(any(), any(), any());
        var files = mock(SysFileService.class);
        var storage = mock(FileStorageService.class);
        SysFile file = new SysFile();
        file.setBucketName("reader");
        file.setObjectName("source.txt");
        file.setFileName("source.txt");
        when(files.findById(88L)).thenReturn(file);
        when(storage.openFile("reader", "source.txt"))
                .thenAnswer(call -> new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8)));
        events = mock(AdminContentEventPublisher.class);
        processor = new ReaderBookImportProcessor(books, session.getMapper(ReaderChapterMapper.class),
                session.getMapper(ReaderTocItemMapper.class), session.getMapper(ReaderBookAssetMapper.class),
                parser, files, storage, events, new DataSourceTransactionManager(source),
                new ReaderImportProperties(), mock(ScheduledExecutorService.class));
    }

    @Test
    void successfulRetryClearsPreviousFailureAndLease() {
        jdbc.update("UPDATE reader_book SET parse_message='previous failure',import_attempts=1 WHERE id=1");

        processor.process(1L);

        ReaderBook book = books.selectById(1L);
        assertEquals(ReaderBook.STATUS_READY, book.getStatus());
        assertNull(book.getParseMessage());
        assertNull(book.getImportToken());
        assertNull(book.getImportLeaseUntil());
        assertEquals(2, book.getImportAttempts());
        assertContentCounts(1);
    }

    @Test
    void failureAfterContentWritesRollsBackAndRetryDoesNotDuplicateContent() {
        doThrow(new IllegalStateException("event persistence unavailable")).doNothing().when(events).publish(any());

        processor.process(1L);

        assertEquals(ReaderBook.STATUS_IMPORTING, books.selectById(1L).getStatus());
        assertContentCounts(0);
        expireLease();
        processor.process(1L);

        assertEquals(ReaderBook.STATUS_READY, books.selectById(1L).getStatus());
        assertContentCounts(1);
    }

    @Test
    void ownershipLostDuringParsingPreventsEveryContentWrite() {
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            expireLease();
            assertEquals(1, books.tryClaimImport(1L, "replacement-worker", 300));
            return parsed;
        }).when(parser).parse(any(), any(), any());

        processor.process(1L);

        assertContentCounts(0);
        ReaderBook book = books.selectById(1L);
        assertEquals(ReaderBook.STATUS_IMPORTING, book.getStatus());
        assertEquals("replacement-worker", book.getImportToken());
        verifyNoInteractions(events);
    }

    private void expireLease() {
        jdbc.update("UPDATE reader_book SET import_lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE id=1");
    }

    private void assertContentCounts(int expected) {
        for (String table : List.of("reader_chapter", "reader_book_asset", "reader_toc_item")) {
            assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE book_id=1", Integer.class), table);
        }
    }
}
