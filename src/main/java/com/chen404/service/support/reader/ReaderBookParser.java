package com.chen404.service.support.reader;

import com.chen404.exception.BadRequestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 小说文件解析器。
 *
 * <p>支持 TXT、EPUB 2/3、HTML、Markdown 与 FB2。所有 XML 入口都关闭外部实体，
 * EPUB 解包同时限制条目数量、单条目大小与总解压大小，避免路径穿越和压缩炸弹。</p>
 */
@Component
public class ReaderBookParser {

    private static final long MAX_SOURCE_SIZE = 60L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_SIZE = 16L * 1024 * 1024;
    private static final long MAX_ZIP_TOTAL_SIZE = 120L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_ASSET_TOTAL_SIZE = 48L * 1024 * 1024;
    private static final int FALLBACK_CHUNK_SIZE = 20_000;
    private static final int ENCODING_SAMPLE_SIZE = 256 * 1024;

    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "^(?:第[0-9０-９零〇○一二三四五六七八九十百千万两]+[章节回篇幕集](?:\\s+|[:：、.-])?.*"
                    + "|(?:序章|序言|楔子|引子|前言|终章|尾声|后记|附录|番外)(?:\\s*\\d+)?(?:\\s+|[:：、.-])?.*"
                    + "|(?:chapter|prologue|epilogue)\\s+[0-9ivxlcdm一二三四五六七八九十百千万]+(?:\\s*[:：.-])?.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VOLUME_HEADING = Pattern.compile(
            "^(?:第[0-9０-９零〇○一二三四五六七八九十百千万两]+[卷部集](?:\\s+|[:：、.-])?.*"
                    + "|(?:卷|部|篇)[0-9０-９零〇○一二三四五六七八九十百千万两]+(?:\\s+|[:：、.-])?.*"
                    + "|(?:part|book|volume)\\s+[0-9ivxlcdm一二三四五六七八九十百千万]+(?:\\s*[:：.-])?.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern HTML_HEADING = Pattern.compile("^(?:h[1-6])$", Pattern.CASE_INSENSITIVE);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt", "epub", "html", "htm", "xhtml", "md", "markdown", "fb2"
    );

    public ParsedReaderBook parse(String fileName, byte[] bytes, String requestedEncoding) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("小说文件不能为空");
        }
        if (bytes.length > MAX_SOURCE_SIZE) {
            throw new BadRequestException("小说文件不能超过 60MB");
        }
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw unsupportedFormat(extension);
        }
        try {
            return switch (extension) {
                case "epub" -> parseEpub(fileName, bytes);
                case "html", "htm", "xhtml" -> parseHtml(fileName, bytes, requestedEncoding);
                case "md", "markdown" -> parseMarkdown(fileName, bytes, requestedEncoding);
                case "fb2" -> parseFb2(fileName, bytes);
                default -> parseText(fileName, bytes, requestedEncoding);
            };
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BadRequestException("小说解析失败：" + safeMessage(exception));
        }
    }

    private ParsedReaderBook parseText(String fileName, byte[] bytes, String requestedEncoding) {
        DecodedText decoded = decodeText(bytes, requestedEncoding);
        String text = normalizeText(decoded.text());
        if (!StringUtils.hasText(text)) {
            throw new BadRequestException("小说正文为空");
        }
        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setFormat("txt");
        parsed.setEncoding(decoded.charset().name());
        parsed.setTitle(stripExtension(fileName));
        parsed.setChapters(splitPlainText(text));
        parsed.setToc(buildTextToc(parsed.getChapters()));
        parsed.setParseMessage("已识别 " + parsed.getChapters().size() + " 个正文章节");
        return ensureComplete(parsed, fileName);
    }

    private ParsedReaderBook parseHtml(String fileName, byte[] bytes, String requestedEncoding) {
        DecodedText decoded = decodeText(bytes, requestedEncoding);
        Document document = Jsoup.parse(decoded.text());
        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setFormat("html");
        parsed.setEncoding(decoded.charset().name());
        parsed.setTitle(firstNonBlank(
                document.title(),
                metaContent(document, "book:title"),
                stripExtension(fileName)
        ));
        parsed.setAuthor(firstNonBlank(
                metaContent(document, "author"),
                metaContent(document, "book:author")
        ));
        parsed.setDescription(metaContent(document, "description"));
        parsed.setLanguage(document.selectFirst("html") == null ? null : document.selectFirst("html").attr("lang"));
        parsed.setChapters(splitHtmlDocument(document, null, Map.of()));
        parsed.setToc(flatToc(parsed.getChapters()));
        parsed.setParseMessage("HTML 标题层级已转换为阅读目录");
        return ensureComplete(parsed, fileName);
    }

    private ParsedReaderBook parseMarkdown(String fileName, byte[] bytes, String requestedEncoding) {
        DecodedText decoded = decodeText(bytes, requestedEncoding);
        String markdown = normalizeText(decoded.text());
        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setFormat("markdown");
        parsed.setEncoding(decoded.charset().name());
        parsed.setTitle(stripExtension(fileName));

        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        String currentTitle = null;
        String currentVolume = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\n", -1)) {
            Matcher heading = MARKDOWN_HEADING.matcher(line.trim());
            if (heading.matches()) {
                String label = stripMarkdownInline(heading.group(2));
                boolean volume = heading.group(1).length() == 1 && VOLUME_HEADING.matcher(label).matches();
                if (volume) {
                    flushMarkdownChapter(chapters, currentTitle, currentVolume, body);
                    currentTitle = null;
                    currentVolume = label;
                    body.setLength(0);
                } else if (heading.group(1).length() <= 2 || CHAPTER_HEADING.matcher(label).matches()) {
                    flushMarkdownChapter(chapters, currentTitle, currentVolume, body);
                    currentTitle = label;
                    body.setLength(0);
                } else {
                    body.append(line).append('\n');
                }
            } else {
                body.append(line).append('\n');
            }
        }
        flushMarkdownChapter(chapters, currentTitle, currentVolume, body);
        if (chapters.isEmpty()) {
            chapters.add(markdownChapter("正文", null, markdown));
        }
        parsed.setChapters(chapters);
        parsed.setToc(buildTextToc(chapters));
        parsed.setParseMessage("Markdown 标题已转换为阅读目录");
        return ensureComplete(parsed, fileName);
    }

    private ParsedReaderBook parseEpub(String fileName, byte[] bytes) throws Exception {
        Map<String, byte[]> entries = readZipEntries(bytes);
        byte[] containerBytes = entries.get("META-INF/container.xml");
        if (containerBytes == null) {
            throw new BadRequestException("EPUB 缺少 META-INF/container.xml");
        }
        org.w3c.dom.Document container = parseXml(containerBytes);
        org.w3c.dom.Element rootfile = firstElement(container, "rootfile");
        String opfPath = rootfile == null ? null : rootfile.getAttribute("full-path");
        if (!StringUtils.hasText(opfPath) || !entries.containsKey(opfPath)) {
            throw new BadRequestException("EPUB 未找到内容清单 OPF");
        }
        org.w3c.dom.Document opf = parseXml(entries.get(opfPath));
        String opfDir = parentPath(opfPath);
        Map<String, ManifestItem> manifest = parseManifest(opf, opfDir);
        List<ManifestItem> spine = parseSpine(opf, manifest);
        if (spine.isEmpty()) {
            throw new BadRequestException("EPUB 正文书脊为空");
        }

        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setFormat("epub");
        parsed.setEncoding("UTF-8");
        parsed.setTitle(firstNonBlank(xmlText(opf, "title"), stripExtension(fileName)));
        parsed.setAuthor(xmlText(opf, "creator"));
        parsed.setDescription(xmlText(opf, "description"));
        parsed.setLanguage(xmlText(opf, "language"));

        String coverId = coverManifestId(opf, manifest);
        Map<String, String> assetPlaceholders = extractEpubAssets(entries, manifest, coverId, parsed);
        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        for (ManifestItem item : spine) {
            byte[] chapterBytes = entries.get(item.path());
            if (chapterBytes == null) {
                continue;
            }
            Document chapterDocument = Jsoup.parse(
                    new ByteArrayInputStream(chapterBytes),
                    null,
                    ""
            );
            List<ParsedReaderBook.Chapter> split = splitHtmlDocument(
                    chapterDocument,
                    item.path(),
                    assetPlaceholders
            );
            if (split.isEmpty()) {
                continue;
            }
            if (split.size() == 1) {
                split.get(0).setSourceHref(item.path());
            } else {
                for (int index = 0; index < split.size(); index++) {
                    split.get(index).setSourceHref(item.path() + "#reader-split-" + index);
                }
            }
            chapters.addAll(split);
        }
        parsed.setChapters(chapters);

        List<ParsedReaderBook.TocNode> toc = parseEpubNavigation(opf, opfDir, manifest, entries);
        bindTocToChapters(toc, chapters);
        parsed.setToc(toc.isEmpty() ? flatToc(chapters) : toc);
        parsed.setParseMessage("EPUB 书脊、" + (toc.isEmpty() ? "推导目录" : "原始多级目录")
                + "与 " + parsed.getAssets().size() + " 个内嵌图片已导入");
        return ensureComplete(parsed, fileName);
    }

    private ParsedReaderBook parseFb2(String fileName, byte[] bytes) throws Exception {
        org.w3c.dom.Document document = parseXml(bytes);
        ParsedReaderBook parsed = new ParsedReaderBook();
        parsed.setFormat("fb2");
        parsed.setEncoding("UTF-8");
        parsed.setTitle(firstNonBlank(xmlText(document, "book-title"), stripExtension(fileName)));
        String firstName = xmlText(document, "first-name");
        String lastName = xmlText(document, "last-name");
        parsed.setAuthor(joinNonBlank(firstName, lastName));
        parsed.setDescription(xmlText(document, "annotation"));
        parsed.setLanguage(xmlText(document, "lang"));

        Map<String, String> assetPlaceholders = extractFb2Assets(document, parsed);
        org.w3c.dom.Element body = firstElement(document, "body");
        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        if (body != null) {
            NodeList sections = body.getChildNodes();
            for (int index = 0; index < sections.getLength(); index++) {
                org.w3c.dom.Node node = sections.item(index);
                if (node instanceof org.w3c.dom.Element section
                        && "section".equalsIgnoreCase(localName(section))) {
                    chapters.add(fb2Section(section, chapters.size(), assetPlaceholders));
                }
            }
        }
        parsed.setChapters(chapters);
        parsed.setToc(flatToc(chapters));
        parsed.setParseMessage("FB2 章节与 " + parsed.getAssets().size() + " 个内嵌图片已导入");
        return ensureComplete(parsed, fileName);
    }

    private List<ParsedReaderBook.Chapter> splitPlainText(String text) {
        List<String> lines = List.of(text.split("\\n", -1));
        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        String currentTitle = null;
        String currentVolume = null;
        StringBuilder body = new StringBuilder();
        boolean foundHeading = false;
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.length() <= 120 && VOLUME_HEADING.matcher(line).matches()) {
                flushPlainChapter(chapters, currentTitle, currentVolume, body);
                currentTitle = null;
                currentVolume = line;
                body.setLength(0);
                foundHeading = true;
            } else if (line.length() <= 120 && CHAPTER_HEADING.matcher(line).matches()) {
                flushPlainChapter(chapters, currentTitle, currentVolume, body);
                currentTitle = line;
                body.setLength(0);
                foundHeading = true;
            } else {
                body.append(rawLine).append('\n');
            }
        }
        flushPlainChapter(chapters, currentTitle, currentVolume, body);
        if (!foundHeading) {
            return splitLongPlainText(text);
        }
        return chapters;
    }

    private List<ParsedReaderBook.Chapter> splitLongPlainText(String text) {
        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder chunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (chunk.length() > 0 && chunk.length() + paragraph.length() > FALLBACK_CHUNK_SIZE) {
                chapters.add(plainChapter("正文 " + (chapters.size() + 1), null, chunk.toString()));
                chunk.setLength(0);
            }
            chunk.append(paragraph.strip()).append("\n\n");
        }
        if (chunk.length() > 0) {
            chapters.add(plainChapter(
                    chapters.isEmpty() ? "正文" : "正文 " + (chapters.size() + 1),
                    null,
                    chunk.toString()
            ));
        }
        return chapters;
    }

    private void flushPlainChapter(
            List<ParsedReaderBook.Chapter> chapters,
            String title,
            String volume,
            StringBuilder body) {
        if (!StringUtils.hasText(body.toString()) && !StringUtils.hasText(title)) {
            return;
        }
        String actualTitle = StringUtils.hasText(title)
                ? title
                : (chapters.isEmpty() ? "序章" : "正文 " + (chapters.size() + 1));
        chapters.add(plainChapter(actualTitle, volume, body.toString()));
    }

    private ParsedReaderBook.Chapter plainChapter(String title, String volume, String content) {
        StringBuilder html = new StringBuilder(content.length() + 256);
        StringBuilder text = new StringBuilder(content.length());
        int blockIndex = 0;
        for (String paragraph : content.split("\\n\\s*\\n")) {
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }
            String normalizedParagraph = paragraph.strip();
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(normalizedParagraph);
            html.append("<p data-reader-block=\"").append(blockIndex++).append("\">");
            String[] lines = paragraph.strip().split("\\n");
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    html.append("<br>");
                }
                html.append(org.jsoup.nodes.Entities.escape(lines[index].stripTrailing()));
            }
            html.append("</p>");
        }
        return new ParsedReaderBook.Chapter(
                safeTitle(title),
                blankToNull(volume),
                null,
                html.toString(),
                text.toString()
        );
    }

    private List<ParsedReaderBook.Chapter> splitHtmlDocument(
            Document source,
            String sourceHref,
            Map<String, String> assetPlaceholders) {
        Document sanitized = sanitizeHtml(source, sourceHref, assetPlaceholders);
        Element body = sanitized.body();
        unwrapChapterContainers(body);
        List<List<Node>> groups = new ArrayList<>();
        List<Node> current = new ArrayList<>();
        for (Node node : new ArrayList<>(body.childNodes())) {
            boolean startsChapter = node instanceof Element element
                    && HTML_HEADING.matcher(element.tagName()).matches()
                    && (element.tagName().equalsIgnoreCase("h1")
                    || element.tagName().equalsIgnoreCase("h2")
                    || CHAPTER_HEADING.matcher(element.text().strip()).matches());
            if (startsChapter && !current.isEmpty()) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(node.clone());
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }

        List<ParsedReaderBook.Chapter> chapters = new ArrayList<>();
        for (List<Node> group : groups) {
            Document chapterDoc = Document.createShell("");
            group.forEach(node -> chapterDoc.body().appendChild(node));
            String text = chapterDoc.body().text();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Element heading = chapterDoc.body().selectFirst("h1, h2, h3, h4, h5, h6");
            String title = heading == null
                    ? "正文 " + (chapters.size() + 1)
                    : safeTitle(heading.text());
            addReaderBlockIndexes(chapterDoc.body());
            chapters.add(new ParsedReaderBook.Chapter(
                    title,
                    null,
                    sourceHref,
                    chapterDoc.body().html(),
                    chapterDoc.body().text()
            ));
        }
        return chapters;
    }

    private void unwrapChapterContainers(Element body) {
        boolean changed;
        do {
            changed = false;
            for (Element child : new ArrayList<>(body.children())) {
                String tag = child.tagName().toLowerCase(Locale.ROOT);
                if (("div".equals(tag) || "section".equals(tag) || "article".equals(tag))
                        && !child.select("h1, h2").isEmpty()) {
                    child.unwrap();
                    changed = true;
                }
            }
        } while (changed);
    }

    private Document sanitizeHtml(
            Document source,
            String sourceHref,
            Map<String, String> assetPlaceholders) {
        Document work = source.clone();
        work.select("script, style, iframe, object, embed, form, input, button, video, audio").remove();
        for (Element image : work.select("img")) {
            String rawSrc = image.attr("src");
            String resolved = sourceHref == null ? rawSrc : resolveRelativePath(sourceHref, rawSrc);
            String placeholder = assetPlaceholders.get(resolved);
            if (placeholder == null) {
                String alt = image.attr("alt");
                image.replaceWith(new TextNode(StringUtils.hasText(alt) ? "〔" + alt + "〕" : ""));
            } else {
                image.attr("src", placeholder);
                image.removeAttr("srcset");
            }
        }
        for (Element link : work.select("a[href]")) {
            String href = link.attr("href").strip();
            if (!href.startsWith("#")) {
                link.removeAttr("href");
            }
        }
        Safelist safelist = Safelist.relaxed()
                .addTags("section", "article", "ruby", "rt", "rp")
                .addAttributes(":all", "id")
                .addAttributes("img", "src", "alt", "title")
                .addProtocols("img", "src", "reader-asset", "http", "https");
        Document cleaned = new Cleaner(safelist).clean(work);
        cleaned.outputSettings().prettyPrint(false);
        return cleaned;
    }

    private void addReaderBlockIndexes(Element body) {
        int index = 0;
        for (Element element : body.select("p, h1, h2, h3, h4, h5, h6, blockquote, pre, li")) {
            if (StringUtils.hasText(element.text())) {
                element.attr("data-reader-block", String.valueOf(index++));
            }
        }
        if (index == 0 && StringUtils.hasText(body.text())) {
            body.attr("data-reader-block", "0");
        }
    }

    private List<ParsedReaderBook.TocNode> buildTextToc(List<ParsedReaderBook.Chapter> chapters) {
        List<ParsedReaderBook.TocNode> roots = new ArrayList<>();
        Map<String, ParsedReaderBook.TocNode> volumes = new LinkedHashMap<>();
        for (int index = 0; index < chapters.size(); index++) {
            ParsedReaderBook.Chapter chapter = chapters.get(index);
            ParsedReaderBook.TocNode chapterNode = new ParsedReaderBook.TocNode(chapter.getTitle(), index);
            if (!StringUtils.hasText(chapter.getVolumeTitle())) {
                roots.add(chapterNode);
                continue;
            }
            ParsedReaderBook.TocNode volume = volumes.computeIfAbsent(chapter.getVolumeTitle(), label -> {
                ParsedReaderBook.TocNode node = new ParsedReaderBook.TocNode();
                node.setLabel(label);
                roots.add(node);
                return node;
            });
            volume.getChildren().add(chapterNode);
        }
        return roots;
    }

    private List<ParsedReaderBook.TocNode> flatToc(List<ParsedReaderBook.Chapter> chapters) {
        List<ParsedReaderBook.TocNode> toc = new ArrayList<>();
        for (int index = 0; index < chapters.size(); index++) {
            toc.add(new ParsedReaderBook.TocNode(chapters.get(index).getTitle(), index));
        }
        return toc;
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalSize = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("EPUB 文件条目过多");
                }
                String name = normalizeZipEntryName(entry.getName());
                byte[] data = readLimited(zip, MAX_ZIP_ENTRY_SIZE);
                totalSize += data.length;
                if (totalSize > MAX_ZIP_TOTAL_SIZE) {
                    throw new BadRequestException("EPUB 解压后内容过大");
                }
                entries.put(name, data);
            }
        }
        return entries;
    }

    private Map<String, ManifestItem> parseManifest(org.w3c.dom.Document opf, String opfDir) {
        Map<String, ManifestItem> manifest = new LinkedHashMap<>();
        NodeList items = elements(opf, "item");
        for (int index = 0; index < items.getLength(); index++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) items.item(index);
            String id = element.getAttribute("id");
            String href = element.getAttribute("href");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(href)) {
                continue;
            }
            String path = resolveRelativePath(opfDir + "/package.opf", href);
            manifest.put(id, new ManifestItem(
                    id,
                    path,
                    element.getAttribute("media-type"),
                    element.getAttribute("properties")
            ));
        }
        return manifest;
    }

    private List<ManifestItem> parseSpine(
            org.w3c.dom.Document opf,
            Map<String, ManifestItem> manifest) {
        List<ManifestItem> spine = new ArrayList<>();
        NodeList refs = elements(opf, "itemref");
        for (int index = 0; index < refs.getLength(); index++) {
            org.w3c.dom.Element ref = (org.w3c.dom.Element) refs.item(index);
            ManifestItem item = manifest.get(ref.getAttribute("idref"));
            if (item != null && isHtmlMediaType(item.mediaType())) {
                spine.add(item);
            }
        }
        return spine;
    }

    private Map<String, String> extractEpubAssets(
            Map<String, byte[]> entries,
            Map<String, ManifestItem> manifest,
            String coverId,
            ParsedReaderBook parsed) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        long total = 0;
        for (ManifestItem item : manifest.values()) {
            if (!isImageMediaType(item.mediaType())) {
                continue;
            }
            byte[] data = entries.get(item.path());
            if (data == null || data.length == 0 || data.length > MAX_ZIP_ENTRY_SIZE) {
                continue;
            }
            total += data.length;
            if (total > MAX_ASSET_TOTAL_SIZE) {
                throw new BadRequestException("EPUB 内嵌图片总大小超过 48MB");
            }
            String placeholder = "reader-asset://asset-" + parsed.getAssets().size();
            parsed.getAssets().add(new ParsedReaderBook.Asset(
                    item.path(),
                    fileNameOf(item.path()),
                    normalizeImageMediaType(item.mediaType(), item.path()),
                    data,
                    Objects.equals(coverId, item.id()) || item.properties().contains("cover-image"),
                    placeholder
            ));
            placeholders.put(item.path(), placeholder);
        }
        return placeholders;
    }

    private String coverManifestId(
            org.w3c.dom.Document opf,
            Map<String, ManifestItem> manifest) {
        for (ManifestItem item : manifest.values()) {
            if (item.properties().contains("cover-image")) {
                return item.id();
            }
        }
        NodeList metas = elements(opf, "meta");
        for (int index = 0; index < metas.getLength(); index++) {
            org.w3c.dom.Element meta = (org.w3c.dom.Element) metas.item(index);
            if ("cover".equalsIgnoreCase(meta.getAttribute("name"))) {
                return meta.getAttribute("content");
            }
        }
        return null;
    }

    private List<ParsedReaderBook.TocNode> parseEpubNavigation(
            org.w3c.dom.Document opf,
            String opfDir,
            Map<String, ManifestItem> manifest,
            Map<String, byte[]> entries) throws Exception {
        ManifestItem nav = manifest.values().stream()
                .filter(item -> item.properties().contains("nav"))
                .findFirst()
                .orElse(null);
        if (nav != null && entries.containsKey(nav.path())) {
            Document document = Jsoup.parse(
                    new ByteArrayInputStream(entries.get(nav.path())),
                    null,
                    ""
            );
            Element tocNav = document.select("nav").stream()
                    .filter(element -> element.attr("epub:type").contains("toc")
                            || element.attr("role").contains("doc-toc"))
                    .findFirst()
                    .orElse(document.selectFirst("nav"));
            if (tocNav != null) {
                Element list = tocNav.selectFirst("ol, ul");
                if (list != null) {
                    return parseHtmlTocList(list, nav.path());
                }
            }
        }

        org.w3c.dom.Element spineElement = firstElement(opf, "spine");
        String tocId = spineElement == null ? null : spineElement.getAttribute("toc");
        ManifestItem ncx = StringUtils.hasText(tocId) ? manifest.get(tocId) : null;
        if (ncx == null) {
            ncx = manifest.values().stream()
                    .filter(item -> "application/x-dtbncx+xml".equalsIgnoreCase(item.mediaType()))
                    .findFirst()
                    .orElse(null);
        }
        if (ncx != null && entries.containsKey(ncx.path())) {
            org.w3c.dom.Document ncxDocument = parseXml(entries.get(ncx.path()));
            org.w3c.dom.Element navMap = firstElement(ncxDocument, "navMap");
            if (navMap != null) {
                return parseNcxChildren(navMap, ncx.path());
            }
        }
        return new ArrayList<>();
    }

    private List<ParsedReaderBook.TocNode> parseHtmlTocList(Element list, String navPath) {
        List<ParsedReaderBook.TocNode> nodes = new ArrayList<>();
        for (Element li : list.children()) {
            if (!"li".equalsIgnoreCase(li.tagName())) {
                continue;
            }
            Element anchor = li.children().stream()
                    .filter(element -> "a".equalsIgnoreCase(element.tagName())
                            || "span".equalsIgnoreCase(element.tagName()))
                    .findFirst()
                    .orElse(null);
            if (anchor == null) {
                continue;
            }
            ParsedReaderBook.TocNode node = new ParsedReaderBook.TocNode();
            node.setLabel(safeTitle(anchor.text()));
            String href = anchor.attr("href");
            bindTocHref(node, navPath, href);
            Element childList = li.children().stream()
                    .filter(element -> "ol".equalsIgnoreCase(element.tagName())
                            || "ul".equalsIgnoreCase(element.tagName()))
                    .findFirst()
                    .orElse(null);
            if (childList != null) {
                node.setChildren(parseHtmlTocList(childList, navPath));
            }
            nodes.add(node);
        }
        return nodes;
    }

    private List<ParsedReaderBook.TocNode> parseNcxChildren(
            org.w3c.dom.Element parent,
            String ncxPath) {
        List<ParsedReaderBook.TocNode> nodes = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            org.w3c.dom.Node child = children.item(index);
            if (!(child instanceof org.w3c.dom.Element element)
                    || !"navPoint".equalsIgnoreCase(localName(element))) {
                continue;
            }
            ParsedReaderBook.TocNode node = new ParsedReaderBook.TocNode();
            node.setLabel(safeTitle(descendantText(element, "text")));
            org.w3c.dom.Element content = descendantElement(element, "content");
            bindTocHref(node, ncxPath, content == null ? null : content.getAttribute("src"));
            node.setChildren(parseNcxChildren(element, ncxPath));
            nodes.add(node);
        }
        return nodes;
    }

    private void bindTocHref(ParsedReaderBook.TocNode node, String basePath, String rawHref) {
        if (!StringUtils.hasText(rawHref)) {
            return;
        }
        int fragmentIndex = rawHref.indexOf('#');
        String href = fragmentIndex >= 0 ? rawHref.substring(0, fragmentIndex) : rawHref;
        node.setFragment(fragmentIndex >= 0 ? rawHref.substring(fragmentIndex + 1) : null);
        node.setSourceHref(resolveRelativePath(basePath, href));
    }

    private void bindTocToChapters(
            List<ParsedReaderBook.TocNode> nodes,
            List<ParsedReaderBook.Chapter> chapters) {
        for (ParsedReaderBook.TocNode node : nodes) {
            String target = stripFragment(node.getSourceHref());
            int best = -1;
            for (int index = 0; index < chapters.size(); index++) {
                String chapterHref = stripFragment(chapters.get(index).getSourceHref());
                if (Objects.equals(target, chapterHref)) {
                    best = index;
                    break;
                }
            }
            if (best < 0 && StringUtils.hasText(node.getLabel())) {
                String normalizedLabel = normalizeLabel(node.getLabel());
                for (int index = 0; index < chapters.size(); index++) {
                    if (normalizeLabel(chapters.get(index).getTitle()).equals(normalizedLabel)) {
                        best = index;
                        break;
                    }
                }
            }
            node.setChapterIndex(best >= 0 ? best : null);
            bindTocToChapters(node.getChildren(), chapters);
        }
    }

    private Map<String, String> extractFb2Assets(
            org.w3c.dom.Document document,
            ParsedReaderBook parsed) {
        Map<String, String> placeholders = new HashMap<>();
        NodeList binaries = elements(document, "binary");
        long total = 0;
        for (int index = 0; index < binaries.getLength(); index++) {
            org.w3c.dom.Element binary = (org.w3c.dom.Element) binaries.item(index);
            String id = binary.getAttribute("id");
            String contentType = firstNonBlank(binary.getAttribute("content-type"), "image/jpeg");
            if (!StringUtils.hasText(id) || !isImageMediaType(contentType)) {
                continue;
            }
            try {
                byte[] data = Base64.getMimeDecoder().decode(binary.getTextContent());
                if (data.length > MAX_ZIP_ENTRY_SIZE) {
                    continue;
                }
                total += data.length;
                if (total > MAX_ASSET_TOTAL_SIZE) {
                    throw new BadRequestException("FB2 内嵌图片总大小超过 48MB");
                }
                String placeholder = "reader-asset://asset-" + parsed.getAssets().size();
                parsed.getAssets().add(new ParsedReaderBook.Asset(
                        id,
                        id,
                        normalizeImageMediaType(contentType, id),
                        data,
                        index == 0,
                        placeholder
                ));
                placeholders.put(id, placeholder);
            } catch (IllegalArgumentException ignored) {
                // 单张损坏图片不应导致整本书无法阅读。
            }
        }
        return placeholders;
    }

    private ParsedReaderBook.Chapter fb2Section(
            org.w3c.dom.Element section,
            int chapterIndex,
            Map<String, String> assetPlaceholders) {
        String title = descendantText(section, "title");
        if (!StringUtils.hasText(title)) {
            title = "正文 " + (chapterIndex + 1);
        }
        Document html = Document.createShell("");
        appendFb2Children(section, html.body(), assetPlaceholders);
        addReaderBlockIndexes(html.body());
        return new ParsedReaderBook.Chapter(
                safeTitle(title),
                null,
                "section-" + chapterIndex,
                html.body().html(),
                html.body().text()
        );
    }

    private void appendFb2Children(
            org.w3c.dom.Element source,
            Element target,
            Map<String, String> assetPlaceholders) {
        NodeList children = source.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            org.w3c.dom.Node child = children.item(index);
            if (child instanceof org.w3c.dom.Text text) {
                if (StringUtils.hasText(text.getTextContent())) {
                    target.appendText(text.getTextContent());
                }
                continue;
            }
            if (!(child instanceof org.w3c.dom.Element element)) {
                continue;
            }
            String name = localName(element).toLowerCase(Locale.ROOT);
            if ("title".equals(name) || "section".equals(name)) {
                if ("section".equals(name)) {
                    appendFb2Children(element, target, assetPlaceholders);
                }
                continue;
            }
            if ("image".equals(name)) {
                String href = attributeByLocalName(element, "href").replace("#", "");
                String placeholder = assetPlaceholders.get(href);
                if (placeholder != null) {
                    target.appendElement("p").appendElement("img").attr("src", placeholder);
                }
                continue;
            }
            String htmlTag = switch (name) {
                case "p", "subtitle" -> "p";
                case "emphasis" -> "em";
                case "strong" -> "strong";
                case "strikethrough" -> "s";
                case "epigraph", "cite" -> "blockquote";
                case "poem", "stanza" -> "div";
                case "v" -> "p";
                case "empty-line" -> "br";
                default -> "span";
            };
            Element childTarget = target.appendElement(htmlTag);
            appendFb2Children(element, childTarget, assetPlaceholders);
        }
    }

    private void flushMarkdownChapter(
            List<ParsedReaderBook.Chapter> chapters,
            String title,
            String volume,
            StringBuilder body) {
        if (!StringUtils.hasText(body.toString()) && !StringUtils.hasText(title)) {
            return;
        }
        String actualTitle = firstNonBlank(title, chapters.isEmpty() ? "正文" : "正文 " + (chapters.size() + 1));
        chapters.add(markdownChapter(actualTitle, volume, body.toString()));
    }

    private ParsedReaderBook.Chapter markdownChapter(String title, String volume, String markdown) {
        Document document = Document.createShell("");
        Element body = document.body();
        Element list = null;
        boolean codeBlock = false;
        StringBuilder code = new StringBuilder();
        for (String rawLine : normalizeText(markdown).split("\\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.strip().startsWith("```")) {
                if (codeBlock) {
                    body.appendElement("pre").appendElement("code").text(code.toString());
                    code.setLength(0);
                }
                codeBlock = !codeBlock;
                continue;
            }
            if (codeBlock) {
                code.append(line).append('\n');
                continue;
            }
            Matcher heading = MARKDOWN_HEADING.matcher(line.strip());
            if (heading.matches()) {
                int level = Math.min(6, heading.group(1).length());
                body.appendElement("h" + level).text(stripMarkdownInline(heading.group(2)));
                list = null;
            } else if (line.matches("^\\s*[-*+]\\s+.+")) {
                if (list == null || !"ul".equals(list.tagName())) {
                    list = body.appendElement("ul");
                }
                list.appendElement("li").html(renderMarkdownInline(line.replaceFirst("^\\s*[-*+]\\s+", "")));
            } else if (line.matches("^\\s*\\d+[.)]\\s+.+")) {
                if (list == null || !"ol".equals(list.tagName())) {
                    list = body.appendElement("ol");
                }
                list.appendElement("li").html(renderMarkdownInline(line.replaceFirst("^\\s*\\d+[.)]\\s+", "")));
            } else if (line.strip().startsWith(">")) {
                body.appendElement("blockquote").html(renderMarkdownInline(line.strip().substring(1).strip()));
                list = null;
            } else if (line.isBlank()) {
                list = null;
            } else {
                body.appendElement("p").html(renderMarkdownInline(line.strip()));
                list = null;
            }
        }
        addReaderBlockIndexes(body);
        return new ParsedReaderBook.Chapter(
                safeTitle(title),
                blankToNull(volume),
                null,
                body.html(),
                body.text()
        );
    }

    private String renderMarkdownInline(String value) {
        String safe = org.jsoup.nodes.Entities.escape(value);
        safe = safe.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        safe = safe.replaceAll("__(.+?)__", "<strong>$1</strong>");
        safe = safe.replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "<em>$1</em>");
        safe = safe.replaceAll("`([^`]+?)`", "<code>$1</code>");
        safe = safe.replaceAll("~~(.+?)~~", "<s>$1</s>");
        return safe;
    }

    private ParsedReaderBook ensureComplete(ParsedReaderBook parsed, String fileName) {
        if (!StringUtils.hasText(parsed.getTitle())) {
            parsed.setTitle(stripExtension(fileName));
        }
        parsed.setTitle(safeTitle(parsed.getTitle()));
        parsed.setAuthor(limit(blankToNull(parsed.getAuthor()), 255));
        parsed.setDescription(limit(blankToNull(parsed.getDescription()), 4_000));
        parsed.setLanguage(limit(blankToNull(parsed.getLanguage()), 40));
        parsed.getChapters().removeIf(chapter -> !StringUtils.hasText(chapter.getContentText()));
        if (parsed.getChapters().isEmpty()) {
            throw new BadRequestException("未能从文件中识别出可阅读正文");
        }
        if (parsed.getToc() == null || parsed.getToc().isEmpty()) {
            parsed.setToc(flatToc(parsed.getChapters()));
        }
        return parsed;
    }

    private DecodedText decodeText(byte[] bytes, String requestedEncoding) {
        if (StringUtils.hasText(requestedEncoding)) {
            try {
                Charset charset = Charset.forName(requestedEncoding.strip());
                return new DecodedText(stripBom(new String(bytes, charset)), charset);
            } catch (Exception exception) {
                throw new BadRequestException("不支持指定编码：" + requestedEncoding);
            }
        }
        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new DecodedText(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new DecodedText(stripBom(new String(bytes, StandardCharsets.UTF_16LE)), StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new DecodedText(stripBom(new String(bytes, StandardCharsets.UTF_16BE)), StandardCharsets.UTF_16BE);
        }
        List<Charset> candidates = new ArrayList<>(List.of(
                StandardCharsets.UTF_8,
                Charset.forName("GB18030"),
                Charset.forName("Big5")
        ));
        if (looksLikeUtf16WithoutBom(bytes)) {
            candidates.add(StandardCharsets.UTF_16LE);
            candidates.add(StandardCharsets.UTF_16BE);
        }
        Charset selectedCharset = candidates.stream()
                .min(Comparator.comparingDouble(charset -> encodingPenalty(decodeSample(bytes, charset))))
                .orElse(StandardCharsets.UTF_8);
        return new DecodedText(stripBom(new String(bytes, selectedCharset)), selectedCharset);
    }

    /**
     * 编码识别只需要观察有代表性的前段文本，避免大文件为每个候选编码都构建完整字符串。
     */
    private String decodeSample(byte[] bytes, Charset charset) {
        int length = Math.min(bytes.length, ENCODING_SAMPLE_SIZE);
        if ((StandardCharsets.UTF_16LE.equals(charset) || StandardCharsets.UTF_16BE.equals(charset))
                && (length & 1) == 1) {
            length--;
        }
        return stripBom(new String(bytes, 0, length, charset));
    }

    private boolean looksLikeUtf16WithoutBom(byte[] bytes) {
        if (bytes.length < 8) {
            return false;
        }
        int sampleLength = Math.min(bytes.length, 4_096);
        int evenZeros = 0;
        int oddZeros = 0;
        for (int index = 0; index < sampleLength; index++) {
            if (bytes[index] == 0) {
                if ((index & 1) == 0) evenZeros++;
                else oddZeros++;
            }
        }
        int pairs = sampleLength / 2;
        return evenZeros > pairs * 0.18 || oddZeros > pairs * 0.18;
    }

    private double encodingPenalty(String value) {
        if (value.isEmpty()) {
            return Double.MAX_VALUE;
        }
        int replacement = 0;
        int control = 0;
        int zero = 0;
        int readable = 0;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '\uFFFD') replacement++;
            if (c == 0) zero++;
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') control++;
            if (Character.isLetterOrDigit(c) || isCjk(c) || Character.isWhitespace(c)) readable++;
        }
        double bad = replacement * 30.0 + control * 12.0 + zero * 20.0;
        return bad / value.length() - (readable * 0.05 / value.length());
    }

    private org.w3c.dom.Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private NodeList elements(org.w3c.dom.Document document, String localName) {
        NodeList namespaced = document.getElementsByTagNameNS("*", localName);
        return namespaced.getLength() > 0 ? namespaced : document.getElementsByTagName(localName);
    }

    private org.w3c.dom.Element firstElement(org.w3c.dom.Document document, String name) {
        NodeList list = elements(document, name);
        return list.getLength() == 0 ? null : (org.w3c.dom.Element) list.item(0);
    }

    private String xmlText(org.w3c.dom.Document document, String name) {
        org.w3c.dom.Element element = firstElement(document, name);
        return element == null ? null : element.getTextContent().strip();
    }

    private org.w3c.dom.Element descendantElement(org.w3c.dom.Element parent, String name) {
        NodeList list = parent.getElementsByTagNameNS("*", name);
        if (list.getLength() == 0) {
            list = parent.getElementsByTagName(name);
        }
        return list.getLength() == 0 ? null : (org.w3c.dom.Element) list.item(0);
    }

    private String descendantText(org.w3c.dom.Element parent, String name) {
        org.w3c.dom.Element element = descendantElement(parent, name);
        return element == null ? null : element.getTextContent().strip();
    }

    private String attributeByLocalName(org.w3c.dom.Element element, String name) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            org.w3c.dom.Node attribute = attributes.item(index);
            if (name.equalsIgnoreCase(attribute.getLocalName())
                    || name.equalsIgnoreCase(attribute.getNodeName())) {
                return attribute.getNodeValue();
            }
        }
        return "";
    }

    private String localName(org.w3c.dom.Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private byte[] readLimited(ZipInputStream input, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        long count = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            count += read;
            if (count > limit) {
                throw new BadRequestException("EPUB 单个条目超过 16MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String normalizeZipEntryName(String rawName) {
        String normalized = rawName.replace('\\', '/');
        Path path = Paths.get(normalized).normalize();
        normalized = path.toString().replace('\\', '/');
        if (path.isAbsolute() || normalized.startsWith("../") || normalized.equals("..")) {
            throw new BadRequestException("EPUB 包含非法路径");
        }
        return normalized;
    }

    private String resolveRelativePath(String basePath, String rawHref) {
        if (!StringUtils.hasText(rawHref)) {
            return "";
        }
        String href = rawHref;
        int fragmentIndex = href.indexOf('#');
        if (fragmentIndex >= 0) {
            href = href.substring(0, fragmentIndex);
        }
        int queryIndex = href.indexOf('?');
        if (queryIndex >= 0) {
            href = href.substring(0, queryIndex);
        }
        try {
            href = URLDecoder.decode(href, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 保留原值，目录文本可能包含非标准百分号。
        }
        String parent = parentPath(basePath);
        Path resolved = Paths.get(parent).resolve(href).normalize();
        String normalized = resolved.toString().replace('\\', '/');
        if (resolved.isAbsolute() || normalized.startsWith("../")) {
            return "";
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return stripBom(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("(?m)[ \\u3000]+$", "")
                .strip();
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String metaContent(Document document, String name) {
        Element element = document.selectFirst("meta[name=\"" + name + "\"], meta[property=\"" + name + "\"]");
        return element == null ? null : element.attr("content");
    }

    private String stripMarkdownInline(String value) {
        return value.replaceAll("[*_`~]", "").strip();
    }

    private String extensionOf(String fileName) {
        String safe = fileName == null ? "" : fileName.strip();
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        String safe = firstNonBlank(fileName, "未命名小说");
        int dot = safe.lastIndexOf('.');
        return safeTitle(dot > 0 ? safe.substring(0, dot) : safe);
    }

    private String parentPath(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String stripFragment(String value) {
        if (value == null) return null;
        int hash = value.indexOf('#');
        return hash < 0 ? value : value.substring(0, hash);
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isHtmlMediaType(String mediaType) {
        return mediaType != null && (mediaType.contains("html") || mediaType.contains("xhtml"));
    }

    private boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private String normalizeImageMediaType(String mediaType, String path) {
        if (StringUtils.hasText(mediaType) && isImageMediaType(mediaType)) {
            return mediaType.toLowerCase(Locale.ROOT);
        }
        String extension = extensionOf(path);
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "image/jpeg";
        };
    }

    private BadRequestException unsupportedFormat(String extension) {
        String format = StringUtils.hasText(extension) ? extension.toUpperCase(Locale.ROOT) : "未知";
        return new BadRequestException(
                "暂不支持 " + format + " 格式。可直接导入 TXT、EPUB、HTML、Markdown 或 FB2；"
                        + "MOBI/AZW3/PDF 请先使用 Calibre 转换为 EPUB。"
        );
    }

    private String safeTitle(String value) {
        String safe = firstNonBlank(value, "未命名章节").replaceAll("\\s+", " ").strip();
        return limit(safe, 500);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? limit(message, 300) : "文件结构不完整或内容已损坏";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.strip();
            }
        }
        return null;
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) parts.add(value.strip());
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) return false;
        }
        return true;
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    public String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record DecodedText(String text, Charset charset) {
    }

    private record ManifestItem(String id, String path, String mediaType, String properties) {
        private ManifestItem {
            properties = properties == null ? "" : properties;
        }
    }
}
