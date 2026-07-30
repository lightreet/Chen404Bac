package com.chen404.service.support.reader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入解析后的中间模型。这里不携带数据库主键，便于解析器单测和事务内批量落库。
 */
@Data
public class ParsedReaderBook {

    private String title;
    private String author;
    private String description;
    private String language;
    private String format;
    private String encoding;
    private String parseMessage;
    private List<Chapter> chapters = new ArrayList<>();
    private List<Asset> assets = new ArrayList<>();
    private List<TocNode> toc = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Chapter {
        private String title;
        private String volumeTitle;
        private String sourceHref;
        private String contentHtml;
        private String contentText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Asset {
        private String sourcePath;
        private String fileName;
        private String mediaType;
        private byte[] data;
        private boolean cover;
        private String placeholder;
    }

    @Data
    @NoArgsConstructor
    public static class TocNode {
        private String label;
        private String sourceHref;
        private String fragment;
        private Integer chapterIndex;
        private List<TocNode> children = new ArrayList<>();

        public TocNode(String label, Integer chapterIndex) {
            this.label = label;
            this.chapterIndex = chapterIndex;
        }
    }
}
