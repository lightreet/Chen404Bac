package com.chen404.service.impl;

import com.chen404.converter.EmojiConverter;
import com.chen404.domain.dto.EmojiImportManifestDTO;
import com.chen404.domain.dto.EmojiImportResultDTO;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class EmojiImportServiceImpl implements EmojiImportService {

    private static final int ZIP_BUFFER_SIZE = 8192;
    private static final int MAX_ZIP_ENTRY_COUNT = 512;
    private static final long MAX_MANIFEST_BYTES = 512 * 1024L;
    private static final long MAX_ASSET_BYTES = 5 * 1024 * 1024L;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 50 * 1024 * 1024L;
    private static final long MAX_COMPRESSION_RATIO = 100L;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp"
    );

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private EmojiService emojiService;

    @Autowired
    private EmojiConverter emojiConverter;

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
    public EmojiImportResultDTO importZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("zip 文件不能为空");
        }

        Map<String, byte[]> fileBytes = new HashMap<>();
        byte[] manifestBytes = null;
        int entryCount = 0;
        long totalUncompressedBytes = 0L;

        try (InputStream is = zipFile.getInputStream(); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRY_COUNT) {
                    throw new IllegalArgumentException("zip 内文件数量不能超过 " + MAX_ZIP_ENTRY_COUNT);
                }

                String name = normalizeZipEntryName(entry.getName());
                long entryLimit = "manifest.json".equalsIgnoreCase(name)
                        ? MAX_MANIFEST_BYTES
                        : MAX_ASSET_BYTES;
                byte[] bytes = readEntry(zis, entryLimit);
                totalUncompressedBytes += bytes.length;
                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("zip 解压后总大小不能超过 50MB");
                }
                assertSafeCompressionRatio(entry, bytes.length);

                if ("manifest.json".equalsIgnoreCase(name)) {
                    if (manifestBytes != null) {
                        throw new IllegalArgumentException("zip 内只能包含一个 manifest.json");
                    }
                    manifestBytes = bytes;
                } else {
                    if (fileBytes.putIfAbsent(name, bytes) != null) {
                        throw new IllegalArgumentException("zip 内存在重复文件: " + name);
                    }
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

        return emojiConverter.toImportResultDTO(manifest.getPack().getPackCode(), success, failed, errors);
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

        if (!StringUtils.hasText(item.getFile())) {
            throw new IllegalArgumentException("type=image 时 item.file 不能为空");
        }
        String assetPath = normalizeZipEntryName(item.getFile());
        byte[] bytes = fileBytes.get(assetPath);
        if (bytes == null) {
            throw new IllegalArgumentException("资源文件不存在: " + item.getFile());
        }

        String ext = requireSupportedImageExtension(assetPath);
        String sha256 = sha256Hex(bytes);
        String objectName = "emoji/packs/" + packCode + "/items/" + item.getShortcode() + "/" + sha256 + "." + ext;
        String assetUrl = fileStorageService.uploadFile(new ByteArrayInputStream(bytes), objectName, guessContentType(ext), bytes.length);

        dto.setType(EmojiItem.Type.IMAGE);
        dto.setUnicode(null);
        dto.setAssetUrl(assetUrl);
        return dto;
    }

    private static String requireSupportedImageExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int idx = lower.lastIndexOf('.');
        if (idx < 0) {
            throw new IllegalArgumentException("表情资源缺少文件扩展名: " + fileName);
        }
        String ext = lower.substring(idx + 1);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的表情资源格式: " + ext);
        }
        return ext;
    }

    private static String guessContentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private static byte[] readEntry(ZipInputStream inputStream, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[ZIP_BUFFER_SIZE];
        long total = 0L;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("zip 内单个文件超过允许大小");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String normalizeZipEntryName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new IllegalArgumentException("zip 内存在空文件名");
        }
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.contains("/../")) {
            throw new IllegalArgumentException("zip 内存在非法路径");
        }
        return normalized;
    }

    private static void assertSafeCompressionRatio(ZipEntry entry, long uncompressedBytes) {
        long compressedBytes = entry.getCompressedSize();
        if (compressedBytes > 0
                && uncompressedBytes > compressedBytes * MAX_COMPRESSION_RATIO) {
            throw new IllegalArgumentException("zip 内文件压缩比异常");
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
