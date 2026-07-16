package com.chen404.service.impl;

import com.chen404.domain.dto.DevelopmentHistoryVO;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.service.GitHubDevelopmentConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevelopmentHistoryServiceImplTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldUsePublicAtomSourceWhenTokenIsMissing() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        String feed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
                  <entry>
                    <id>tag:github.com,2008:Grit::Commit/1234567890abcdef</id>
                    <link rel="alternate" href="https://github.com/lightreet/Chen404Fro/commit/1234567890abcdef" />
                    <title>新增开发历程页面</title>
                    <updated>2026-07-15T08:00:00Z</updated>
                    <media:thumbnail url="https://avatars.example.com/lightreet.png" />
                    <author><name>lightreet</name><uri>https://github.com/lightreet</uri></author>
                  </entry>
                  <entry>
                    <id>tag:github.com,2008:Grit::Commit/abcdef1234567890</id>
                    <link rel="alternate" href="https://github.com/lightreet/Chen404Fro/commit/abcdef1234567890" />
                    <title>Merge branch 'main' into dev</title>
                    <updated>2026-07-15T09:00:00Z</updated>
                    <author><name>lightreet</name><uri>https://github.com/lightreet</uri></author>
                  </entry>
                </feed>
                """;
        server.createContext("/lightreet/Chen404Fro/commits/main.atom", exchange -> {
            byte[] body = feed.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/atom+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        GitHubDevelopmentAdminConfigDTO settings = new GitHubDevelopmentAdminConfigDTO();
        settings.setOwner("lightreet");
        settings.setRepositories(List.of("Chen404Fro"));
        settings.setBranch("main");
        settings.setWebBaseUrl("http://localhost:" + server.getAddress().getPort());
        settings.setApiBaseUrl("http://localhost:" + server.getAddress().getPort());
        settings.setToken("");
        settings.setCacheMinutes(30);
        settings.setApiCommitLimit(100);
        settings.setRequestTimeoutSeconds(12);
        GitHubDevelopmentConfigService configService = mock(GitHubDevelopmentConfigService.class);
        when(configService.getEffectiveConfig()).thenReturn(settings);

        DevelopmentHistoryVO history = new DevelopmentHistoryServiceImpl(configService).getDevelopmentHistory();

        assertTrue(history.isAvailable());
        assertFalse(history.isStale());
        assertEquals(1, history.getTotalCommits());
        assertEquals(1, history.getContributorCount());
        assertEquals("新增开发历程页面", history.getCommits().get(0).getMessage());
        assertEquals("1234567", history.getCommits().get(0).getShortSha());
        assertEquals("前端", history.getCommits().get(0).getRepositoryLabel());
        assertEquals("atom", history.getRepositories().get(0).getSource());
    }

    @Test
    void shouldBypassCacheWhenEffectiveConfigChanges() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lightreet/RepoOne/commits/main.atom", exchange ->
                writeAtomResponse(exchange, "1111111111111111", "第一个仓库提交"));
        server.createContext("/lightreet/RepoTwo/commits/main.atom", exchange ->
                writeAtomResponse(exchange, "2222222222222222", "第二个仓库提交"));
        server.start();

        String webBaseUrl = "http://localhost:" + server.getAddress().getPort();
        GitHubDevelopmentAdminConfigDTO firstSettings = settings(webBaseUrl, "RepoOne");
        GitHubDevelopmentAdminConfigDTO secondSettings = settings(webBaseUrl, "RepoTwo");
        GitHubDevelopmentConfigService configService = mock(GitHubDevelopmentConfigService.class);
        when(configService.getEffectiveConfig()).thenReturn(firstSettings, secondSettings);
        DevelopmentHistoryServiceImpl service = new DevelopmentHistoryServiceImpl(configService);

        DevelopmentHistoryVO first = service.getDevelopmentHistory();
        DevelopmentHistoryVO second = service.getDevelopmentHistory();

        assertEquals("第一个仓库提交", first.getCommits().get(0).getMessage());
        assertEquals("第二个仓库提交", second.getCommits().get(0).getMessage());
    }

    private GitHubDevelopmentAdminConfigDTO settings(String webBaseUrl, String repository) {
        GitHubDevelopmentAdminConfigDTO settings = new GitHubDevelopmentAdminConfigDTO();
        settings.setOwner("lightreet");
        settings.setRepositories(List.of(repository));
        settings.setBranch("main");
        settings.setWebBaseUrl(webBaseUrl);
        settings.setApiBaseUrl(webBaseUrl);
        settings.setToken("");
        settings.setCacheMinutes(30);
        settings.setApiCommitLimit(100);
        settings.setRequestTimeoutSeconds(12);
        return settings;
    }

    private void writeAtomResponse(
            HttpExchange exchange,
            String sha,
            String title) throws IOException {
        String feed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry>
                    <id>tag:github.com,2008:Grit::Commit/%s</id>
                    <link rel="alternate" href="https://github.com/lightreet/repository/commit/%s" />
                    <title>%s</title>
                    <updated>2026-07-16T08:00:00Z</updated>
                    <author><name>lightreet</name><uri>https://github.com/lightreet</uri></author>
                  </entry>
                </feed>
                """.formatted(sha, sha, title);
        byte[] body = feed.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/atom+xml; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
