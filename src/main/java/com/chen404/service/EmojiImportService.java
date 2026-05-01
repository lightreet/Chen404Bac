package com.chen404.service;

import com.chen404.domain.dto.EmojiImportManifestDTO;
import com.chen404.domain.dto.EmojiImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

public interface EmojiImportService {

    /**
     * 导入 zip（包含 manifest.json 与资源文件）。
     */
    EmojiImportResultDTO importZip(MultipartFile zipFile);

    EmojiImportManifestDTO parseManifest(byte[] manifestBytes);
}
