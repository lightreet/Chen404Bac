package com.chen404.service.support.reader;

import com.chen404.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaderBookParserTest {

    private final ReaderBookParser parser = new ReaderBookParser();

    @Test
    void shouldDetectGb18030AndBuildVolumeTocForText() {
        String content = """
                第一卷 初见
                第一章 雨夜

                她在雨里看见一盏灯。

                第二章 来信

                信纸上只有短短一行字。
                """;

        ParsedReaderBook parsed = parser.parse(
                "雨夜.txt",
                content.getBytes(Charset.forName("GB18030")),
                null
        );

        assertEquals("txt", parsed.getFormat());
        assertEquals("GB18030", parsed.getEncoding());
        assertEquals(2, parsed.getChapters().size());
        assertEquals("第一卷 初见", parsed.getChapters().get(0).getVolumeTitle());
        assertEquals(1, parsed.getToc().size());
        assertEquals(2, parsed.getToc().get(0).getChildren().size());
        assertTrue(parsed.getChapters().get(0).getContentHtml().contains("data-reader-block"));
    }

    @Test
    void shouldParseEpub3SpineNestedTocAndCoverAsset() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
        entries.put("META-INF/container.xml", """
                <?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试 EPUB</dc:title>
                    <dc:creator>测试作者</dc:creator>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="cover" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("OEBPS/nav.xhtml", """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body><nav epub:type="toc"><ol>
                    <li><span>第一卷</span><ol>
                      <li><a href="text/ch1.xhtml#start">第一章</a></li>
                      <li><a href="text/ch2.xhtml">第二章</a></li>
                    </ol></li>
                  </ol></nav></body>
                </html>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("OEBPS/text/ch1.xhtml", """
                <html><body><h1 id="start">第一章</h1><p>正文一。</p>
                <img src="../images/cover.png" alt="插图"/></body></html>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("OEBPS/text/ch2.xhtml", """
                <html><body><h1>第二章</h1><p>正文二。</p></body></html>
                """.getBytes(StandardCharsets.UTF_8));
        entries.put("OEBPS/images/cover.png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        ParsedReaderBook parsed = parser.parse("test.epub", zip(entries), null);

        assertEquals("测试 EPUB", parsed.getTitle());
        assertEquals("测试作者", parsed.getAuthor());
        assertEquals(2, parsed.getChapters().size());
        assertEquals(1, parsed.getAssets().size());
        assertTrue(parsed.getAssets().get(0).isCover());
        assertTrue(parsed.getChapters().get(0).getContentHtml().contains("reader-asset://asset-0"));
        assertEquals("第一卷", parsed.getToc().get(0).getLabel());
        assertEquals(2, parsed.getToc().get(0).getChildren().size());
        assertEquals(0, parsed.getToc().get(0).getChildren().get(0).getChapterIndex());
        assertEquals("start", parsed.getToc().get(0).getChildren().get(0).getFragment());
    }

    @Test
    void shouldSanitizeHtmlAndConvertHeadingsToChapters() {
        String html = """
                <html><head><title>安全阅读</title><meta name="author" content="作者甲"></head>
                <body>
                  <script>alert('x')</script>
                  <h1>第一章</h1><p onclick="bad()">正文一</p>
                  <img src="https://tracker.invalid/pixel.png" alt="远程图"/>
                  <h1>第二章</h1><p>正文二</p>
                </body></html>
                """;

        ParsedReaderBook parsed = parser.parse(
                "safe.html",
                html.getBytes(StandardCharsets.UTF_8),
                null
        );

        assertEquals(2, parsed.getChapters().size());
        assertFalse(parsed.getChapters().get(0).getContentHtml().contains("script"));
        assertFalse(parsed.getChapters().get(0).getContentHtml().contains("onclick"));
        assertFalse(parsed.getChapters().get(0).getContentHtml().contains("tracker.invalid"));
        assertTrue(parsed.getChapters().get(0).getContentText().contains("远程图"));
    }

    @Test
    void shouldParseMarkdownAndFb2() {
        ParsedReaderBook markdown = parser.parse(
                "story.md",
                """
                        # 第一卷
                        ## 第一章
                        **加粗**正文。
                        ## 第二章
                        > 引文
                        """.getBytes(StandardCharsets.UTF_8),
                null
        );
        assertEquals(2, markdown.getChapters().size());
        assertTrue(markdown.getChapters().get(0).getContentHtml().contains("<strong>加粗</strong>"));

        ParsedReaderBook fb2 = parser.parse(
                "story.fb2",
                """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                          <description><title-info>
                            <book-title>FB2 故事</book-title>
                            <author><first-name>明</first-name><last-name>陈</last-name></author>
                            <lang>zh</lang>
                          </title-info></description>
                          <body>
                            <section><title><p>第一章</p></title><p>正文一</p></section>
                            <section><title><p>第二章</p></title><p>正文二</p></section>
                          </body>
                        </FictionBook>
                        """.getBytes(StandardCharsets.UTF_8),
                null
        );
        assertEquals("FB2 故事", fb2.getTitle());
        assertEquals(2, fb2.getChapters().size());
        assertTrue(fb2.getChapters().get(0).getContentText().contains("正文一"));
    }

    @Test
    void shouldRejectUnsupportedAndZipTraversal() throws Exception {
        assertThrows(
                BadRequestException.class,
                () -> parser.parse("story.mobi", "x".getBytes(StandardCharsets.UTF_8), null)
        );

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../escape.txt", "bad".getBytes(StandardCharsets.UTF_8));
        assertThrows(
                BadRequestException.class,
                () -> parser.parse("bad.epub", zip(entries), null)
        );
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
