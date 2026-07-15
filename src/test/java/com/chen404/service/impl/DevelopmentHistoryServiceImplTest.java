package com.chen404.service.impl;

import com.chen404.config.GitHubDevelopmentProperties;
import com.chen404.domain.dto.DevelopmentHistoryVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        GitHubDevelopmentProperties properties = new GitHubDevelopmentProperties();
        properties.setRepositories(List.of("Chen404Fro"));
        properties.setWebBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setToken("");

        DevelopmentHistoryVO history = new DevelopmentHistoryServiceImpl(properties).getDevelopmentHistory();

        assertTrue(history.isAvailable());
        assertFalse(history.isStale());
        assertEquals(1, history.getTotalCommits());
        assertEquals(1, history.getContributorCount());
        assertEquals("新增开发历程页面", history.getCommits().get(0).getMessage());
        assertEquals("1234567", history.getCommits().get(0).getShortSha());
        assertEquals("前端", history.getCommits().get(0).getRepositoryLabel());
        assertEquals("atom", history.getRepositories().get(0).getSource());
    }
}
