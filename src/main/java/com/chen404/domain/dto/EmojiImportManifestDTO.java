package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "表情包批量导入 manifest.json 结构")
public class EmojiImportManifestDTO {

    @Data
    public static class Pack {
        private String packCode;
        private String name;
        private String description;
        private Integer enabled;
        private Integer sort;
        private String iconUrl;
    }

    @Data
    public static class Item {
        private String shortcode;
        private String label;
        private String category;
        /**
         * image / unicode
         */
        private String type;
        /**
         * type=image 时必填，例如 items/basic_smile.webp
         */
        private String file;
        /**
         * type=unicode 时使用
         */
        private String unicode;
        private Integer enabled;
        private Integer sort;
        private Integer width;
        private Integer height;
    }

    private Pack pack;
    private List<Item> items;
}

