package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/** 在图片压缩前拆分 JPEG 实况原文件；兼容视频前有厂商数据、视频后有索引的文件。 */
public final class TravelMotionPhotoReader {
    private static final Set<String> VIDEO_BOXES = Set.of(
            "ftyp", "moov", "mdat", "free", "skip", "wide", "uuid", "moof", "mfra", "sidx", "styp", "emsg", "prft");

    private TravelMotionPhotoReader() { }

    /** 普通图片返回 null；只读取有界本地文件，提取出的媒体还须通过图片校验与 ffprobe 校验。 */
    public static MotionPhoto read(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try (var input = file.getInputStream()) {
            byte[] signature = input.readNBytes(2);
            if (signature.length != 2 || unsigned(signature[0]) != 0xff || unsigned(signature[1]) != 0xd8) return null;
            if (file.getSize() > TravelVideoProcessor.MAX_VIDEO_BYTES) {
                throw new BadRequestException("实况照片不能超过 60 MB");
            }
            byte[] remainder = input.readNBytes((int) TravelVideoProcessor.MAX_VIDEO_BYTES - 1);
            if (remainder.length + 2 > TravelVideoProcessor.MAX_VIDEO_BYTES) {
                throw new BadRequestException("实况照片不能超过 60 MB");
            }
            byte[] bytes = new byte[remainder.length + 2];
            System.arraycopy(signature, 0, bytes, 0, 2);
            System.arraycopy(remainder, 0, bytes, 2, remainder.length);
            int imageEnd = jpegEnd(bytes);
            if (imageEnd < 0) return null;
            // 不依赖扩展名、厂商 XMP 前缀或易失的 offset；只在 JPEG 结束之后寻找真实 BMFF 容器。
            for (int start = imageEnd; start <= bytes.length - 16; start++) {
                if (bytes[start + 4] != 'f' || bytes[start + 5] != 't'
                        || bytes[start + 6] != 'y' || bytes[start + 7] != 'p') continue;
                long headerLength = uint32(bytes, start);
                if (headerLength < 16 || headerLength > 4096 || headerLength % 4 != 0) continue;
                int videoEnd = videoEnd(bytes, start);
                if (videoEnd < 0) throw new BadRequestException("实况照片的动态内容已损坏，请上传原文件或从相册导出视频");
                return new MotionPhoto(
                        new TravelVideoProcessor.MediaFile(file.getOriginalFilename(), "image/jpeg", Arrays.copyOf(bytes, imageEnd)),
                        new TravelVideoProcessor.MediaFile("motion.mp4", "video/mp4", Arrays.copyOfRange(bytes, start, videoEnd)));
            }
            return null;
        } catch (IOException ex) {
            throw new BadRequestException("无法读取实况照片，请重新选择原文件");
        }
    }

    /** 跳过 JPEG 段负载和转义字节，避免把 EXIF 缩略图或压缩数据内的标记误当主图终点。 */
    private static int jpegEnd(byte[] bytes) {
        int position = 2;
        while (position < bytes.length) {
            if (unsigned(bytes[position++]) != 0xff) return -1;
            while (position < bytes.length && unsigned(bytes[position]) == 0xff) position++;
            if (position >= bytes.length) return -1;
            int marker = unsigned(bytes[position++]);
            if (marker == 0xd9) return position;
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
            if (position + 2 > bytes.length) return -1;
            int length = unsigned(bytes[position]) * 256 + unsigned(bytes[position + 1]);
            if (length < 2 || length > bytes.length - position) return -1;
            position += length;
            if (marker != 0xda) continue;
            while (position + 1 < bytes.length) {
                if (unsigned(bytes[position]) != 0xff) { position++; continue; }
                int next = unsigned(bytes[position + 1]);
                if (next == 0 || next >= 0xd0 && next <= 0xd7) { position += 2; continue; }
                break;
            }
        }
        return -1;
    }

    private static int videoEnd(byte[] bytes, int start) {
        int position = start;
        boolean movie = false;
        boolean media = false;
        while (position <= bytes.length - 8) {
            String type = new String(bytes, position + 4, 4, StandardCharsets.US_ASCII);
            if (!VIDEO_BOXES.contains(type)) break; // vivo 等厂商可能在完整视频之后追加索引。
            long length = uint32(bytes, position);
            int header = 8;
            if (length == 1) {
                if (position > bytes.length - 16 || uint32(bytes, position + 8) != 0) return -1;
                length = uint32(bytes, position + 12);
                header = 16;
            } else if (length == 0) {
                length = bytes.length - position;
            }
            if (length < header || length > bytes.length - position) return -1;
            movie |= "moov".equals(type);
            media |= "mdat".equals(type);
            position += (int) length;
        }
        return movie && media ? position : -1;
    }

    private static long uint32(byte[] bytes, int position) {
        return (long) unsigned(bytes[position]) << 24 | (long) unsigned(bytes[position + 1]) << 16
                | (long) unsigned(bytes[position + 2]) << 8 | unsigned(bytes[position + 3]);
    }

    private static int unsigned(byte value) { return value & 0xff; }

    /** 原始静态封面与待验证的视频；持久化仍沿用同一影像条目的 imageUrl/videoUrl。 */
    public record MotionPhoto(MultipartFile image, MultipartFile video) { }
}
