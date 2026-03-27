package com.chen404.service;

import com.chen404.domain.dto.EmojiImportManifestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface EmojiImportService {

    /**
     * 导入 zip（包含 manifest.json + 资源文件）
     * 返回：successCount/failCount/packCode 等汇总信息
     */
    Map<String, Object> importZip(MultipartFile zipFile);

    EmojiImportManifestDTO parseManifest(byte[] manifestBytes);
}

