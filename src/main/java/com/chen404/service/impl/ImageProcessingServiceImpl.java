package com.chen404.service.impl;

import com.chen404.config.ImageProcessingProperties;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.service.ImageProcessingService;
import com.chen404.service.ProcessedImage;
import com.luciad.imageio.webp.WebPWriteParam;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * 静图转 WebP；动图 GIF 不处理，保持原图上传以支持动态封面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingServiceImpl implements ImageProcessingService {

    private static final String WEBP_FORMAT = "webp";
    private static final String MIME_WEBP = "image/webp";
    private static final String EXT_WEBP = ".webp";
    private static final String COMPRESSION_LOSSY = "Lossy";
    private static final String COMPRESSION_LOSSLESS = "Lossless";

    private final ImageProcessingProperties properties;

    @PostConstruct
    public void init() {
        ImageIO.scanForPlugins();
        if (!ImageIO.getImageWritersByFormatName(WEBP_FORMAT).hasNext()) {
            log.warn("未注册 WebP ImageIO 编码器，图片压缩将不可用（请检查 webp-imageio 依赖）");
        }
    }

    @Override
    public Optional<ProcessedImage> process(MultipartFile file, String refType) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (!ImageIO.getImageWritersByFormatName(WEBP_FORMAT).hasNext()) {
            return Optional.empty();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return Optional.empty();
        }

        final byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("无法读取上传文件");
        }
        if (raw.length == 0) {
            throw new BadRequestException("文件不能为空");
        }

        if (isGifSignature(raw) && isAnimatedGif(raw)) {
            log.debug("动图 GIF 不压缩，保持原文件上传");
            return Optional.empty();
        }

        BufferedImage decoded = decode(raw);
        BufferedImage transformed = transform(decoded, refType);
        BufferedImage normalized = toEncoderFriendly(transformed);
        byte[] webp = encodeWebP(normalized, imageHasAlpha(normalized));

        return Optional.of(new ProcessedImage(webp, MIME_WEBP, EXT_WEBP));
    }

    private BufferedImage decode(byte[] raw) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                throw new BadRequestException("无法解析图片（格式损坏或不支持）");
            }
            return img;
        } catch (IOException e) {
            throw new BadRequestException("无法解析图片（格式损坏或不支持）");
        }
    }

    private BufferedImage transform(BufferedImage src, String refType) {
        try {
            if (SysFile.RefType.AVATAR.equals(refType)) {
                return avatarSquare(src, properties.getAvatarSize());
            }
            return limitMaxEdge(src, maxEdgeFor(refType));
        } catch (IOException e) {
            throw new BadRequestException("图片处理失败");
        }
    }

    private int maxEdgeFor(String refType) {
        if (SysFile.RefType.ARTICLE_COVER.equals(refType)) {
            return properties.getMaxEdgeArticleCover();
        }
        return properties.getMaxEdgeArticleContent();
    }

    private static BufferedImage limitMaxEdge(BufferedImage src, int maxEdge) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxEdge && h <= maxEdge) {
            return src;
        }
        return Thumbnails.of(src)
                .size(maxEdge, maxEdge)
                .keepAspectRatio(true)
                .asBufferedImage();
    }

    private static BufferedImage avatarSquare(BufferedImage src, int outSize) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        return Thumbnails.of(src)
                .sourceRegion(Positions.CENTER, side, side)
                .size(outSize, outSize)
                .keepAspectRatio(false)
                .asBufferedImage();
    }

    private static BufferedImage toEncoderFriendly(BufferedImage src) {
        boolean alpha = imageHasAlpha(src);
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (src.getType() == type) {
            return src;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, copy.getWidth(), copy.getHeight());
            }
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static boolean imageHasAlpha(BufferedImage image) {
        return image.getColorModel().hasAlpha();
    }

    private byte[] encodeWebP(BufferedImage image, boolean hasAlpha) {
        boolean lossless = hasAlpha && properties.isLosslessWebpForAlpha();
        float q = Math.min(100, Math.max(1, properties.getWebpQuality())) / 100f;

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(WEBP_FORMAT);
        if (!writers.hasNext()) {
            throw new IllegalStateException("WebP writer missing");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam baseParam = writer.getDefaultWriteParam();
            if (!(baseParam instanceof WebPWriteParam wp)) {
                throw new IllegalStateException("Expected WebPWriteParam");
            }
            wp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            wp.setCompressionType(lossless ? COMPRESSION_LOSSLESS : COMPRESSION_LOSSY);
            if (!lossless) {
                wp.setCompressionQuality(q);
            }
            writer.write(null, new IIOImage(image, null, null), wp);
            ios.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("WebP 编码失败", e);
            throw new BadRequestException("图片编码失败，请换一张图或稍后再试");
        } finally {
            writer.dispose();
        }
    }

    private static boolean isGifSignature(byte[] bytes) {
        return bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a';
    }

    private static boolean isAnimatedGif(byte[] raw) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                return reader.getNumImages(true) > 1;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("GIF 帧数检测失败，按静图处理: {}", e.getMessage());
            return false;
        }
    }
}
