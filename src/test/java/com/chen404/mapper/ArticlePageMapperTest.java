package com.chen404.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.chen404.config.MyBatisPlusConfig;
import com.chen404.domain.access.ArticleReadScope;
import com.chen404.domain.dto.ArticleSearchCriteria;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.User;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/** 使用实际分页插件与 SQL 对照对象权限，避免两条权限表达随扩展发生偏移。 */
class ArticlePageMapperTest {
    private JdbcTemplate jdbc;
    private ArticleMapper mapper;
    private final QueryCounter counter = new QueryCounter();

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:article-pages;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new ClassPathResource("db/article-test-schema.sql"),
                new ClassPathResource("db/migration/V2026090802__add_article_edit_version.sql")).execute(source);
        jdbc.execute("CREATE TABLE article_tag (article_id BIGINT, tag_id BIGINT)");
        jdbc.execute("CREATE TABLE user_article_like (article_id BIGINT, user_id BIGINT, create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE user_article_favorite (article_id BIGINT, user_id BIGINT, create_time TIMESTAMP)");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ArticleMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/ArticleMapper.xml"));
        factory.setPlugins(new MyBatisPlusConfig().mybatisPlusInterceptor(), counter);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ArticleMapper.class);
        counter.sql.clear();
    }

    @Test
    void databaseReadScopeMatchesObjectPermissionsIncludingUnknownValuesAndDisabledOwners() {
        List<Article> articles = new ArrayList<>();
        for (Integer status : Arrays.asList(0, 1, 2, 99, null)) {
            for (Integer visibility : Arrays.asList(0, 1, 2, 3, 99, null)) {
                for (long author : List.of(7L, 8L)) {
                    articles.add(insert(articles.size() + 1L, author, status, visibility));
                }
            }
        }
        for (User viewer : Arrays.asList(null, user(7, 1, 0, "user"), user(8, 1, 1, "user"),
                user(9, 1, 0, "admin"), user(7, 0, 1, "admin"))) {
            ArticleReadScope scope = ArticleReadScope.forUser(viewer);
            Set<Long> expected = articles.stream().filter(scope::canRead).map(Article::getId).collect(Collectors.toSet());
            Page<Article> result = mapper.selectReadablePage(new Page<>(1, 100),
                    new ArticleSearchCriteria(null, null, null, null, null), scope, false);
            assertEquals(expected, result.getRecords().stream().map(Article::getId).collect(Collectors.toSet()));
            assertEquals(expected.size(), result.getTotal());
        }
        Article privateDraft = insert(100L, 7L, 0, 3);
        assertFalse(ArticleReadScope.forUser(user(7, 0, 1, "admin")).canRead(privateDraft));
        assertTrue(ArticleReadScope.forUser(user(7, 1, 0, "user")).canRead(privateDraft));
        assertFalse(ArticleReadScope.forUser(null).canRead(insert(101L, 7L, 1, null)));
    }

    @Test
    void largeCandidateSetStillNeedsOnlyCountAndOnePageQueryAndDoesNotReadBody() {
        List<Object[]> rows = LongStream.rangeClosed(1, 1200).mapToObj(id -> new Object[]{id}).toList();
        jdbc.batchUpdate("INSERT INTO article (id,title,status,visibility,content,password,create_time) "
                + "VALUES (?,'title',1,0,'large body','secret',CURRENT_TIMESTAMP)", rows);
        Page<Article> result = mapper.selectReadablePage(new Page<>(2, 10),
                new ArticleSearchCriteria(1, null, null, null, null), ArticleReadScope.forUser(null), false);
        assertEquals(1200, result.getTotal());
        assertEquals(10, result.getRecords().size());
        assertEquals(1190L, result.getRecords().get(0).getId());
        assertEquals(2, counter.sql.size(), counter.sql.toString());
        assertTrue(result.getRecords().stream().allMatch(article -> article.getContent() == null && article.getPassword() == null));
    }

    @Test
    void categoryTagAuthorAndKeywordFiltersAreAppliedBeforeCounting() {
        insert(1L, 7L, 1, 0);
        insert(2L, 8L, 1, 0);
        insert(3L, 7L, 1, 3);
        jdbc.update("UPDATE article SET category_id=5,title='target' WHERE id IN (1,2,3)");
        jdbc.update("INSERT INTO article_tag VALUES (1,9),(2,9),(3,9)");
        Page<Article> result = mapper.selectReadablePage(new Page<>(1, 10),
                new ArticleSearchCriteria(1, 5L, 9L, 7L, "target"), ArticleReadScope.forUser(null), false);
        assertEquals(1, result.getTotal());
        assertEquals(1L, result.getRecords().get(0).getId());
    }

    @Test
    void relatedListsFilterBeforePaginationAndPreserveRelationOrder() {
        insert(1L, 7L, 1, 0);
        insert(2L, 7L, 1, 1);
        insert(3L, 7L, 1, 3);
        for (String table : List.of("user_article_like", "user_article_favorite")) {
            jdbc.update("INSERT INTO " + table + " VALUES (1,8,'2026-01-01'),(2,8,'2026-01-02'),(3,8,'2026-01-03')");
        }
        for (boolean likes : List.of(true, false)) {
            Page<Article> result = mapper.selectRelatedPage(new Page<>(1, 1), 8L, likes,
                    ArticleReadScope.forUser(user(8, 1, 0, "user")));
            assertEquals(2, result.getTotal());
            assertEquals(2L, result.getRecords().get(0).getId());
        }
    }

    private Article insert(Long id, Long authorId, Integer status, Integer visibility) {
        jdbc.update("INSERT INTO article (id,title,author_id,status,visibility,create_time) VALUES (?,'title',?,?,?,CURRENT_TIMESTAMP)",
                id, authorId, status, visibility);
        Article article = new Article();
        article.setId(id);
        article.setAuthorId(authorId);
        article.setStatus(status);
        article.setVisibility(visibility);
        return article;
    }

    private User user(long id, int status, int trust, String role) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setTrustLevel(trust);
        user.setRoleCode(role);
        return user;
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class QueryCounter implements Interceptor {
        final List<String> sql = new ArrayList<>();
        public Object intercept(Invocation invocation) throws Throwable {
            sql.add(((StatementHandler) invocation.getTarget()).getBoundSql().getSql());
            return invocation.proceed();
        }
    }
}
