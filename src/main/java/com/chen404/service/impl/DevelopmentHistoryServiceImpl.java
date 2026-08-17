package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.domain.dto.DevelopmentCommitVO;
import com.chen404.domain.dto.DevelopmentHistoryVO;
import com.chen404.domain.dto.DevelopmentRepositoryVO;
import com.chen404.domain.dto.GitHubDevelopmentAdminConfigDTO;
import com.chen404.service.DevelopmentHistoryService;
import com.chen404.service.GitHubDevelopmentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * GitHub 开发历程服务实现。
 * <p>
 * 配置 Token 时优先使用 REST API 获取较完整的记录；未配置或 API 失败时，
 * 回退到公开 Atom 提交源，并通过内存缓存限制上游请求频率。
 */
@Slf4j
@Service
public class DevelopmentHistoryServiceImpl implements DevelopmentHistoryService {

    private static final String USER_AGENT = "Chen404-Development-History";
    private static final String SOURCE_API = "api";
    private static final String SOURCE_ATOM = "atom";
    private static final String REPOSITORY_FRONTEND = "Chen404Fro";
    private static final String REPOSITORY_BACKEND = "Chen404Bac";
    private static final String LABEL_FRONTEND = "前端";
    private static final String LABEL_BACKEND = "后端";
    private static final String LABEL_PROJECT = "项目";
    private static final int MIN_CACHE_MINUTES = 1;
    private static final int MIN_TIMEOUT_SECONDS = 3;
    private static final int GITHUB_API_PAGE_SIZE = 100;
    private static final int FIRST_API_PAGE = 1;
    private static final int SHORT_SHA_LENGTH = 7;
    private static final int FAILURE_CACHE_MINUTES = 5;

    private final GitHubDevelopmentConfigService configService;
    private final HttpClient httpClient;
    private final Object refreshLock = new Object();

    private volatile CacheEntry cacheEntry;

    public DevelopmentHistoryServiceImpl(GitHubDevelopmentConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public DevelopmentHistoryVO getDevelopmentHistory() {
        GitHubDevelopmentAdminConfigDTO settings = configService.getEffectiveConfig();
        String configFingerprint = configFingerprint(settings);
        CacheEntry current = cacheEntry;
        if (isFresh(current, configFingerprint)) {
            return current.data();
        }

        synchronized (refreshLock) {
            current = cacheEntry;
            if (isFresh(current, configFingerprint)) {
                return current.data();
            }
            CacheEntry previous = hasSameConfig(current, configFingerprint) ? current : null;
            return refreshHistory(previous, settings, configFingerprint);
        }
    }

    @Override
    public DevelopmentHistoryVO refreshDevelopmentHistory() {
        GitHubDevelopmentAdminConfigDTO settings = configService.getEffectiveConfig();
        String configFingerprint = configFingerprint(settings);
        synchronized (refreshLock) {
            CacheEntry previous = hasSameConfig(cacheEntry, configFingerprint) ? cacheEntry : null;
            return refreshHistory(previous, settings, configFingerprint);
        }
    }

    private DevelopmentHistoryVO refreshHistory(
            CacheEntry previous,
            GitHubDevelopmentAdminConfigDTO settings,
            String configFingerprint) {
        List<DevelopmentCommitVO> commits = new ArrayList<>();
        List<DevelopmentRepositoryVO> repositories = new ArrayList<>();
        List<String> failedRepositories = new ArrayList<>();

        for (String repository : normalizedRepositories(settings)) {
            try {
                RepositoryFetchResult result = fetchRepository(repository, settings);
                commits.addAll(result.commits());
                repositories.add(toRepositorySummary(repository, result, settings));
            } catch (Exception e) {
                failedRepositories.add(repository);
                log.warn("[GITHUB_HISTORY_REPO_FAIL] owner={} repository={} message={}",
                        settings.getOwner(), repository, e.getMessage());
            }
        }

        if (commits.isEmpty() && previous != null) {
            DevelopmentHistoryVO stale = copyHistory(previous.data());
            stale.setStale(true);
            stale.setNotice("GitHub 暂时无法同步，当前展示上次成功获取的记录");
            cacheEntry = new CacheEntry(
                    stale,
                    Instant.now().plus(Duration.ofMinutes(FAILURE_CACHE_MINUTES)),
                    configFingerprint);
            return stale;
        }

        commits.sort(Comparator.comparing(DevelopmentCommitVO::getCommittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        DevelopmentHistoryVO history = new DevelopmentHistoryVO();
        history.setCommits(commits);
        history.setRepositories(repositories);
        history.setTotalCommits(commits.size());
        history.setContributorCount(countContributors(commits));
        history.setGeneratedAt(Instant.now());
        history.setAvailable(!commits.isEmpty());
        history.setStale(false);
        history.setNotice(resolveNotice(failedRepositories, commits.isEmpty(), settings));

        cacheEntry = new CacheEntry(
                history,
                Instant.now().plus(resolveCacheDuration(settings)),
                configFingerprint);
        log.info("[GITHUB_HISTORY_REFRESH] owner={} repositories={} commits={} source={}",
                settings.getOwner(), repositories.size(), commits.size(), summarizeSources(repositories));
        return history;
    }

    private RepositoryFetchResult fetchRepository(
            String repository,
            GitHubDevelopmentAdminConfigDTO settings) throws Exception {
        if (StringUtils.hasText(settings.getToken())) {
            try {
                return new RepositoryFetchResult(
                        filterDisplayableCommits(fetchFromApi(repository, settings)), SOURCE_API);
            } catch (Exception e) {
                log.warn("[GITHUB_HISTORY_API_FALLBACK] owner={} repository={} message={}",
                        settings.getOwner(), repository, e.getMessage());
            }
        }
        return new RepositoryFetchResult(filterDisplayableCommits(fetchFromAtom(repository, settings)), SOURCE_ATOM);
    }

    private List<DevelopmentCommitVO> filterDisplayableCommits(List<DevelopmentCommitVO> commits) {
        return commits.stream()
                .filter(commit -> !isMergeCommit(commit.getMessage()))
                .toList();
    }

    private boolean isMergeCommit(String message) {
        return StringUtils.hasText(message)
                && message.trim().toLowerCase(Locale.ROOT).startsWith("merge ");
    }

    private List<DevelopmentCommitVO> fetchFromApi(
            String repository,
            GitHubDevelopmentAdminConfigDTO settings) throws Exception {
        List<DevelopmentCommitVO> commits = new ArrayList<>();
        Set<String> collectedShas = new LinkedHashSet<>();
        int page = FIRST_API_PAGE;

        while (true) {
            JSONArray items = fetchApiPage(repository, settings, page);
            if (items.isEmpty()) {
                break;
            }

            int previousSize = commits.size();
            for (DevelopmentCommitVO commit : parseApiCommits(repository, items)) {
                if (!StringUtils.hasText(commit.getSha()) || collectedShas.add(commit.getSha())) {
                    commits.add(commit);
                }
            }

            if (items.size() < GITHUB_API_PAGE_SIZE) {
                break;
            }
            if (commits.size() == previousSize) {
                log.warn("[GITHUB_HISTORY_API_PAGE_REPEAT] owner={} repository={} page={}",
                        settings.getOwner(), repository, page);
                break;
            }
            page++;
        }
        return commits;
    }

    private JSONArray fetchApiPage(
            String repository,
            GitHubDevelopmentAdminConfigDTO settings,
            int page) throws Exception {
        String endpoint = normalizeBaseUrl(settings.getApiBaseUrl())
                + "/repos/" + settings.getOwner() + "/" + repository
                + "/commits?sha=" + encode(settings.getBranch())
                + "&per_page=" + GITHUB_API_PAGE_SIZE
                + "&page=" + page;

        HttpRequest request = baseRequest(endpoint, settings)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + settings.getToken().trim())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        return JSON.parseArray(send(request));
    }

    private List<DevelopmentCommitVO> fetchFromAtom(
            String repository,
            GitHubDevelopmentAdminConfigDTO settings) throws Exception {
        String endpoint = normalizeBaseUrl(settings.getWebBaseUrl())
                + "/" + settings.getOwner() + "/" + repository
                + "/commits/" + settings.getBranch() + ".atom";
        HttpRequest request = baseRequest(endpoint, settings)
                .header("Accept", "application/atom+xml")
                .GET()
                .build();
        return parseAtomCommits(repository, send(request));
    }

    private HttpRequest.Builder baseRequest(String endpoint, GitHubDevelopmentAdminConfigDTO settings) {
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds(settings)))
                .header("User-Agent", USER_AGENT);
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub 返回状态码 " + response.statusCode());
        }
        return response.body();
    }

    private List<DevelopmentCommitVO> parseApiCommits(String repository, JSONArray items) {
        List<DevelopmentCommitVO> commits = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            JSONObject item = items.getJSONObject(index);
            JSONObject commit = item.getJSONObject("commit");
            if (commit == null) {
                continue;
            }
            JSONObject commitAuthor = commit.getJSONObject("author");
            JSONObject author = item.getJSONObject("author");

            DevelopmentCommitVO result = baseCommit(repository, item.getString("sha"));
            result.setMessage(firstLine(commit.getString("message")));
            result.setAuthorName(commitAuthor == null ? null : commitAuthor.getString("name"));
            result.setCommittedAt(parseInstant(commitAuthor == null ? null : commitAuthor.getString("date")));
            result.setUrl(item.getString("html_url"));
            if (author != null) {
                result.setAuthorLogin(author.getString("login"));
                result.setAuthorAvatarUrl(author.getString("avatar_url"));
            }
            commits.add(result);
        }
        return commits;
    }

    private List<DevelopmentCommitVO> parseAtomCommits(String repository, String body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));

        NodeList entries = document.getElementsByTagNameNS("*", "entry");
        List<DevelopmentCommitVO> commits = new ArrayList<>();
        for (int index = 0; index < entries.getLength(); index++) {
            Element entry = (Element) entries.item(index);
            String sha = lastPathSegment(text(entry, "id"));
            DevelopmentCommitVO result = baseCommit(repository, sha);
            result.setMessage(firstLine(text(entry, "title")));
            result.setCommittedAt(parseInstant(text(entry, "updated")));
            result.setUrl(attribute(entry, "link", "href"));

            Element author = firstElement(entry, "author");
            if (author != null) {
                result.setAuthorName(text(author, "name"));
                result.setAuthorLogin(lastPathSegment(text(author, "uri")));
            }
            result.setAuthorAvatarUrl(attribute(entry, "thumbnail", "url"));
            commits.add(result);
        }
        return commits;
    }

    private DevelopmentCommitVO baseCommit(String repository, String sha) {
        DevelopmentCommitVO commit = new DevelopmentCommitVO();
        commit.setSha(sha);
        commit.setShortSha(shortSha(sha));
        commit.setRepository(repository);
        commit.setRepositoryLabel(resolveRepositoryLabel(repository));
        return commit;
    }

    private DevelopmentRepositoryVO toRepositorySummary(
            String repository,
            RepositoryFetchResult result,
            GitHubDevelopmentAdminConfigDTO settings) {
        DevelopmentRepositoryVO summary = new DevelopmentRepositoryVO();
        summary.setName(repository);
        summary.setLabel(resolveRepositoryLabel(repository));
        summary.setCommitCount(result.commits().size());
        summary.setSource(result.source());
        summary.setUrl(normalizeBaseUrl(settings.getWebBaseUrl())
                + "/" + settings.getOwner() + "/" + repository);
        return summary;
    }

    private int countContributors(List<DevelopmentCommitVO> commits) {
        Set<String> contributors = new LinkedHashSet<>();
        for (DevelopmentCommitVO commit : commits) {
            String identity = StringUtils.hasText(commit.getAuthorLogin())
                    ? commit.getAuthorLogin()
                    : commit.getAuthorName();
            if (StringUtils.hasText(identity)) {
                contributors.add(identity.trim().toLowerCase(Locale.ROOT));
            }
        }
        return contributors.size();
    }

    private String resolveNotice(
            List<String> failedRepositories,
            boolean empty,
            GitHubDevelopmentAdminConfigDTO settings) {
        if (empty) {
            return "GitHub 暂时无法同步，请稍后再试";
        }
        if (!failedRepositories.isEmpty()) {
            return "部分仓库暂时无法同步：" + String.join("、", failedRepositories);
        }
        if (!StringUtils.hasText(settings.getToken())) {
            return "当前使用 GitHub 公开提交源，配置 Token 后可展示更完整记录";
        }
        return null;
    }

    private DevelopmentHistoryVO copyHistory(DevelopmentHistoryVO source) {
        DevelopmentHistoryVO copy = new DevelopmentHistoryVO();
        copy.setCommits(new ArrayList<>(source.getCommits()));
        copy.setRepositories(new ArrayList<>(source.getRepositories()));
        copy.setTotalCommits(source.getTotalCommits());
        copy.setContributorCount(source.getContributorCount());
        copy.setGeneratedAt(source.getGeneratedAt());
        copy.setAvailable(source.isAvailable());
        copy.setStale(source.isStale());
        copy.setNotice(source.getNotice());
        return copy;
    }

    private List<String> normalizedRepositories(GitHubDevelopmentAdminConfigDTO settings) {
        if (settings.getRepositories() == null) {
            return List.of();
        }
        return settings.getRepositories().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean isFresh(CacheEntry entry, String configFingerprint) {
        return hasSameConfig(entry, configFingerprint) && Instant.now().isBefore(entry.expiresAt());
    }

    private boolean hasSameConfig(CacheEntry entry, String configFingerprint) {
        return entry != null && Objects.equals(entry.configFingerprint(), configFingerprint);
    }

    private Duration resolveCacheDuration(GitHubDevelopmentAdminConfigDTO settings) {
        return Duration.ofMinutes(Math.max(settings.getCacheMinutes(), MIN_CACHE_MINUTES));
    }

    private int resolveTimeoutSeconds(GitHubDevelopmentAdminConfigDTO settings) {
        return Math.max(settings.getRequestTimeoutSeconds(), MIN_TIMEOUT_SECONDS);
    }

    private String configFingerprint(GitHubDevelopmentAdminConfigDTO settings) {
        return Integer.toHexString(Objects.hash(
                settings.getOwner(),
                settings.getRepositories(),
                settings.getBranch(),
                settings.getToken(),
                settings.getCacheMinutes(),
                settings.getRequestTimeoutSeconds(),
                settings.getApiBaseUrl(),
                settings.getWebBaseUrl()));
    }

    private String resolveRepositoryLabel(String repository) {
        if (REPOSITORY_FRONTEND.equalsIgnoreCase(repository)) {
            return LABEL_FRONTEND;
        }
        if (REPOSITORY_BACKEND.equalsIgnoreCase(repository)) {
            return LABEL_BACKEND;
        }
        return LABEL_PROJECT;
    }

    private String summarizeSources(List<DevelopmentRepositoryVO> repositories) {
        return repositories.stream()
                .map(DevelopmentRepositoryVO::getSource)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private String text(Element parent, String localName) {
        Element element = firstElement(parent, localName);
        return element == null ? null : element.getTextContent().trim();
    }

    private Element firstElement(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node instanceof Element element ? element : null;
    }

    private String attribute(Element parent, String localName, String attributeName) {
        Element element = firstElement(parent, localName);
        return element == null ? null : element.getAttribute(attributeName);
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Instant.parse(value.trim());
    }

    private String firstLine(String value) {
        if (!StringUtils.hasText(value)) {
            return "未命名提交";
        }
        return value.strip().split("\\R", 2)[0].trim();
    }

    private String shortSha(String sha) {
        if (!StringUtils.hasText(sha) || sha.length() <= SHORT_SHA_LENGTH) {
            return sha;
        }
        return sha.substring(0, SHORT_SHA_LENGTH);
    }

    private String lastPathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record RepositoryFetchResult(List<DevelopmentCommitVO> commits, String source) {
    }

    private record CacheEntry(DevelopmentHistoryVO data, Instant expiresAt, String configFingerprint) {
    }
}
