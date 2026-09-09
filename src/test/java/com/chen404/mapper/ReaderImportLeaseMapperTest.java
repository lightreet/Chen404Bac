package com.chen404.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 实际执行租约迁移与 Mapper SQL，验证并发认领及过期执行器隔离。 */
class ReaderImportLeaseMapperTest {
    private JdbcTemplate jdbc;
    private ReaderBookMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:reader-lease;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE reader_book (id BIGINT PRIMARY KEY, status VARCHAR(20), "
                + "deleted INT DEFAULT 0, parse_message VARCHAR(1000))");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V2026090901__add_reader_import_lease.sql")).execute(source);
        jdbc.update("INSERT INTO reader_book (id,status) VALUES (1,'importing'),(2,'importing'),(3,'ready')");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ReaderBookMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/ReaderBookMapper.xml"));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ReaderBookMapper.class);
    }

    @Test
    void onlyOneExecutorCanClaimTheSameBook() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        var first = claim("first", barrier);
        var second = claim("second", barrier);
        assertEquals(1, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
        assertEquals(1, jdbc.queryForObject("SELECT import_attempts FROM reader_book WHERE id=1", Integer.class));
        assertEquals(List.of(2L), mapper.selectRecoverableImports(10));
    }

    @Test
    void expiredLeaseCanBeRecoveredButStaleWorkerCannotRenewWriteOrFailIt() {
        assertEquals(1, mapper.tryClaimImport(1L, "old", 300));
        expire(1L);
        assertEquals(0, mapper.renewImportLease(1L, "old", 300));
        assertNull(mapper.selectClaimForUpdate(1L, "old"));
        assertEquals(1, mapper.tryClaimImport(1L, "new", 300));
        assertEquals(0, mapper.renewImportLease(1L, "old", 300));
        assertEquals(0, mapper.retryOrFailImport(1L, "old", "stale failure", true, 3, 1));
        assertNull(mapper.selectClaimForUpdate(1L, "old"));
        assertNotNull(mapper.selectClaimForUpdate(1L, "new"));
        assertEquals(1, mapper.renewImportLease(1L, "new", 300));
    }

    @Test
    void retriesAreDelayedAndStopAtConfiguredAttemptLimit() {
        assertEquals(1, mapper.tryClaimImport(1L, "attempt-1", 300));
        assertEquals(1, mapper.retryOrFailImport(1L, "attempt-1", "temporary", false, 2, 30));
        assertEquals(List.of(2L), mapper.selectRecoverableImports(10));
        expire(1L);
        assertEquals(1, mapper.tryClaimImport(1L, "attempt-2", 300));
        assertEquals(1, mapper.retryOrFailImport(1L, "attempt-2", "failed", false, 2, 30));
        assertEquals("failed", jdbc.queryForObject("SELECT status FROM reader_book WHERE id=1", String.class));
        expire(1L);
        assertEquals(List.of(2L), mapper.selectRecoverableImports(10));
        assertEquals(0, mapper.tryClaimImport(1L, "attempt-3", 300));
    }

    @Test
    void scanIsBoundedAndIgnoresDeletedBooks() {
        assertEquals(List.of(1L), mapper.selectRecoverableImports(1));
        jdbc.update("UPDATE reader_book SET deleted=1 WHERE id=1");
        assertEquals(List.of(2L), mapper.selectRecoverableImports(10));
        assertEquals(0, mapper.tryClaimImport(1L, "deleted", 300));
    }

    private CompletableFuture<Integer> claim(String token, CyclicBarrier barrier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return mapper.tryClaimImport(1L, token, 300);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private void expire(Long bookId) {
        jdbc.update("UPDATE reader_book SET import_lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE id=?", bookId);
    }
}
