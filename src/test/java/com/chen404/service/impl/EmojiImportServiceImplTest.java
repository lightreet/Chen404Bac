package com.chen404.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EmojiImportServiceImplTest {

    @Test
    void shouldRejectTraversalPathBeforeReadingManifest() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../outside.png"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "emoji.zip",
                "application/zip",
                bytes.toByteArray()
        );

        assertThrows(IllegalArgumentException.class, () -> new EmojiImportServiceImpl().importZip(file));
    }

    @Test
    void shouldRejectExcessiveEntryCount() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < 513; i++) {
                zip.putNextEntry(new ZipEntry("items/" + i + ".png"));
                zip.write(i);
                zip.closeEntry();
            }
        }
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "emoji.zip",
                "application/zip",
                bytes.toByteArray()
        );

        assertThrows(IllegalArgumentException.class, () -> new EmojiImportServiceImpl().importZip(file));
    }
}
