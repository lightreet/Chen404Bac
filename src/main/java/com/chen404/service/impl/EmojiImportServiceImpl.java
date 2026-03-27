package com.chen404.service.impl;

import com.chen404.domain.dto.EmojiImportManifestDTO;
import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.service.EmojiImportService;
import com.chen404.service.EmojiService;
import com.chen404.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class EmojiImportServiceImpl implements EmojiImportService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private EmojiService emojiService;

    @Override
    public EmojiImportManifestDTO parseManifest(byte[] manifestBytes) {
        try {
            return objectMapper.readValue(manifestBytes, EmojiImportManifestDTO.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("manifest.json 解析失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("zip 文件不能为空");
        }

        Map<String, byte[]> fileBytes = new HashMap<>();
        byte[] manifestBytes = null;

        try (InputStream is = zipFile.getInputStream(); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] bytes = zis.readAllBytes();
                if ("manifest.json".equalsIgnoreCase(name)) {
                    manifestBytes = bytes;
                } else {
                    fileBytes.put(name, bytes);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取 zip 失败");
        }

        if (manifestBytes == null) {
            throw new IllegalArgumentException("zip 内缺少 manifest.json");
        }

        EmojiImportManifestDTO manifest = parseManifest(manifestBytes);
        if (manifest.getPack() == null || !StringUtils.hasText(manifest.getPack().getPackCode())) {
            throw new IllegalArgumentException("manifest.pack.packCode 不能为空");
        }

        // upsert pack
        EmojiPackUpsertDTO packDto = new EmojiPackUpsertDTO();
        packDto.setPackCode(manifest.getPack().getPackCode());
        packDto.setName(manifest.getPack().getName());
        packDto.setDescription(manifest.getPack().getDescription());
        packDto.setEnabled(manifest.getPack().getEnabled());
        packDto.setSort(manifest.getPack().getSort());
        packDto.setIconUrl(manifest.getPack().getIconUrl());
        emojiService.upsertPack(packDto);

        int success = 0;
        int failed = 0;
        List<Map<String, String>> errors = new ArrayList<>();

        List<EmojiImportManifestDTO.Item> items = manifest.getItems() == null ? List.of() : manifest.getItems();
        for (EmojiImportManifestDTO.Item item : items) {
            try {
                EmojiItemUpsertDTO itemDto = toItemUpsertDto(manifest.getPack().getPackCode(), item, fileBytes);
                EmojiItem saved = emojiService.upsertItem(itemDto);
                success += saved != null ? 1 : 0;
            } catch (RuntimeException ex) {
                failed++;
                errors.add(Map.of(
                        "shortcode", item != null ? String.valueOf(item.getShortcode()) : "",
                        "error", ex.getMessage() == null ? "导入失败" : ex.getMessage()
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("packCode", manifest.getPack().getPackCode());
        result.put("successCount", success);
        result.put("failCount", failed);
        result.put("errors", errors);
        return result;
    }

    private EmojiItemUpsertDTO toItemUpsertDto(String packCode, EmojiImportManifestDTO.Item item, Map<String, byte[]> fileBytes) {
        if (item == null || !StringUtils.hasText(item.getShortcode())) {
            throw new IllegalArgumentException("item.shortcode 不能为空");
        }
        if (!StringUtils.hasText(item.getCategory())) {
            throw new IllegalArgumentException("item.category 不能为空");
        }
        String type = StringUtils.hasText(item.getType()) ? item.getType().trim().toLowerCase() : "image";

        EmojiItemUpsertDTO dto = new EmojiItemUpsertDTO();
        dto.setPackCode(packCode);
        dto.setShortcode(item.getShortcode());
        dto.setLabel(StringUtils.hasText(item.getLabel()) ? item.getLabel() : item.getShortcode());
        dto.setCategory(item.getCategory());
        dto.setEnabled(item.getEnabled() == null ? 1 : item.getEnabled());
        dto.setSort(item.getSort() == null ? 0 : item.getSort());
        dto.setWidth(item.getWidth());
        dto.setHeight(item.getHeight());

        if ("unicode".equals(type)) {
            if (!StringUtils.hasText(item.getUnicode())) {
                throw new IllegalArgumentException("type=unicode 时 item.unicode 不能为空");
            }
            dto.setType(EmojiItem.Type.UNICODE);
            dto.setUnicode(item.getUnicode());
            dto.setAssetUrl(null);
            return dto;
        }

        // image
        if (!StringUtils.hasText(item.getFile())) {
            throw new IllegalArgumentException("type=image 时 item.file 不能为空");
        }
        byte[] bytes = fileBytes.get(item.getFile());
        if (bytes == null) {
            throw new IllegalArgumentException("资源文件不存在: " + item.getFile());
        }

        String ext = guessExt(item.getFile());
        String sha1 = sha1Hex(bytes);
        String objectName = "emoji/packs/" + packCode + "/items/" + item.getShortcode() + "/" + sha1 + "." + ext;

        String assetUrl = fileStorageService.uploadFile(new ByteArrayInputStream(bytes), objectName, guessContentType(ext), bytes.length);

        dto.setType(EmojiItem.Type.IMAGE);
        dto.setUnicode(null);
        dto.setAssetUrl(assetUrl);
        return dto;
    }

    private static String guessExt(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int idx = lower.lastIndexOf('.');
        if (idx < 0) return "webp";
        return lower.substring(idx + 1);
    }

    private static String guessContentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha1 计算失败");
        }
    }
}

