package com.chen404.service.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** 生成有真实 JPEG 主图的实况容器，视频内容可替换为真实 ffmpeg 样本。 */
public final class MotionPhotoFixtures {
    private MotionPhotoFixtures() { }

    public static byte[] jpeg() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        return output.toByteArray();
    }

    public static byte[] box(String type, byte[] content) {
        return ByteBuffer.allocate(8 + content.length).putInt(8 + content.length)
                .put(type.getBytes(StandardCharsets.US_ASCII)).put(content).array();
    }

    public static byte[] container() throws Exception {
        return join(box("ftyp", "isom0000mp42".getBytes(StandardCharsets.US_ASCII)),
                box("moov", new byte[8]), box("mdat", new byte[32]));
    }

    public static byte[] join(byte[]... parts) throws Exception {
        var output = new ByteArrayOutputStream();
        for (byte[] part : parts) output.write(part);
        return output.toByteArray();
    }
}
