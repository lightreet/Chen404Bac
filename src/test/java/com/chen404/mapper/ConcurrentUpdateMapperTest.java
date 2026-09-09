package com.chen404.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chen404.config.MyBatisPlusConfig;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 执行实际 SQL，重放“读取旧对象、其他操作提交、旧对象写回”的交错顺序。 */
class ConcurrentUpdateMapperTest {
    private JdbcTemplate jdbc;
    private ArticleMapper articles;
    private UserMapper users;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:concurrent-updates;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE article (id BIGINT PRIMARY KEY, title VARCHAR(100), summary VARCHAR(500), "
                + "content CLOB, content_html CLOB, cover_image VARCHAR(255), cover_file_id BIGINT, author_id BIGINT, "
                + "category_id BIGINT, status INT, view_count INT, like_count INT, comment_count INT, "
                + "is_top INT, is_recommend INT, is_original INT, original_url VARCHAR(255), password VARCHAR(255), "
                + "visibility INT, comment_policy INT, publish_time TIMESTAMP, create_time TIMESTAMP, "
                + "update_time TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V2026090802__add_article_edit_version.sql")));
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(64), password VARCHAR(255), "
                + "nickname VARCHAR(64), email VARCHAR(255), phone VARCHAR(32), avatar VARCHAR(255), avatar_file_id BIGINT, "
                + "bio VARCHAR(255), status INT, trust_level INT, profile_visibility INT, email_public INT, "
                + "email_verified INT, phone_verified INT, last_login_time TIMESTAMP, last_login_ip VARCHAR(64), "
                + "create_time TIMESTAMP, update_time TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.update("INSERT INTO article (id,title,content,author_id,status,visibility,comment_policy,view_count,"
                + "like_count,comment_count,is_top,is_recommend) VALUES (1,'old','old',7,1,0,1,10,2,3,0,0)");
        jdbc.update("INSERT INTO sys_user (id,username,password,nickname,status,trust_level,profile_visibility,email_public) "
                + "VALUES (7,'reader','old-hash','old',1,0,1,0)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(ArticleMapper.class);
        configuration.addMapper(UserMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/ArticleMapper.xml"));
        factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor());
        var session = new SqlSessionTemplate(factory.getObject());
        articles = session.getMapper(ArticleMapper.class);
        users = session.getMapper(UserMapper.class);
    }

    @Test
    void articleEditMustPreserveConcurrentCountersAndCuratorFlags() {
        Article edit = articles.selectById(1L);
        articles.incrementViewCount(1L);
        articles.incrementLikeCount(1L);
        jdbc.update("UPDATE article SET comment_count=comment_count+1,is_top=1,is_recommend=1 WHERE id=1");
        edit.setContent("new body");
        edit.setAuthorId(999L);
        assertEquals(1, articles.updateEditableFields(edit, edit.getVersion(), false));

        Article saved = articles.selectById(1L);
        assertEquals("new body", saved.getContent());
        assertEquals(11, saved.getViewCount());
        assertEquals(3, saved.getLikeCount());
        assertEquals(4, saved.getCommentCount());
        assertEquals(1, saved.getIsTop());
        assertEquals(1, saved.getIsRecommend());
        assertEquals(7L, saved.getAuthorId());
        assertEquals(1, saved.getVersion());
    }

    @Test
    void onlyOneConcurrentEditWithSameVersionMayCommit() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Integer> first = editConcurrently("first", barrier);
        CompletableFuture<Integer> second = editConcurrently("second", barrier);
        List<Integer> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        assertTrue(results.contains(0));
        assertTrue(results.contains(1));
        assertEquals(1, articles.selectById(1L).getVersion());
    }

    @Test
    void profileWriteMustPreserveConcurrentPasswordStatusAndTrustChanges() {
        User stale = users.selectById(7L);
        jdbc.update("UPDATE sys_user SET password='reset-hash',status=0,trust_level=1,nickname='admin' WHERE id=7");
        stale.setNickname("new nickname");
        stale.setBio(null);
        assertEquals(1, users.updateProfileFields(stale));

        User saved = users.selectById(7L);
        assertEquals("new nickname", saved.getNickname());
        assertEquals("reset-hash", saved.getPassword());
        assertEquals(0, saved.getStatus());
        assertEquals(1, saved.getTrustLevel());
        assertNull(saved.getBio());
    }

    @Test
    void loginAuditMustPreserveProfileAndTrustAndRejectChangedCredentials() {
        User stale = users.selectById(7L);
        jdbc.update("UPDATE sys_user SET nickname='new nickname',trust_level=1 WHERE id=7");
        assertEquals(1, users.updateLoginAudit(7L, stale.getPassword(), LocalDateTime.now(), "127.0.0.1"));
        assertEquals("new nickname", users.selectById(7L).getNickname());
        assertEquals(1, users.selectById(7L).getTrustLevel());
        assertEquals(1, users.updatePasswordIfUnchanged(7L, "old-hash", "new-hash"));
        assertEquals(0, users.updateLoginAudit(7L, stale.getPassword(), LocalDateTime.now(), "192.0.2.1"));
        assertEquals("127.0.0.1", users.selectById(7L).getLastLoginIp());
    }

    @Test
    void passwordAndTrustWritesMustNotOverwriteEachOther() {
        assertEquals(1, users.updateTrustLevel(7L, 1));
        assertEquals(1, users.updatePasswordIfUnchanged(7L, "old-hash", "new-hash"));
        assertEquals(0, users.updatePasswordIfUnchanged(7L, "old-hash", "stale-hash"));
        assertEquals(1, users.updateTrustLevel(7L, 0));
        assertEquals("new-hash", users.selectById(7L).getPassword());
        assertEquals(0, users.selectById(7L).getTrustLevel());
    }

    @Test
    void disabledAccountMustRejectConcurrentLoginAndPasswordChange() {
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=7");
        assertEquals(0, users.updateLoginAudit(7L, "old-hash", LocalDateTime.now(), "127.0.0.1"));
        assertEquals(0, users.updatePasswordIfUnchanged(7L, "old-hash", "new-hash"));
        assertEquals("old-hash", users.selectById(7L).getPassword());
    }

    private CompletableFuture<Integer> editConcurrently(String content, CyclicBarrier barrier) {
        return CompletableFuture.supplyAsync(() -> {
            Article edit = articles.selectById(1L);
            edit.setContent(content);
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return articles.updateEditableFields(edit, edit.getVersion(), true);
        });
    }
}
