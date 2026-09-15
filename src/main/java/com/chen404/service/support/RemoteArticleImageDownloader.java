package com.chen404.service.support;

import com.chen404.domain.ArticleImageImportConstraints;
import com.chen404.exception.BadRequestException;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 仅下载公网栅格图片。DNS 校验发生在实际建立连接的解析器中，避免预检后再次解析的重绑定窗口。
 * 不使用系统代理、Cookie、鉴权头或自动重定向；每一跳重新检查 URL 与连接地址。
 */
@Component
public class RemoteArticleImageDownloader {
    private static final int MAX_REDIRECTS = 3;
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 20;
    private static final long MAX_IMAGE_PIXELS = 40_000_000;
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "image/png", "jpeg", "image/jpeg", "gif", "image/gif",
            "webp", "image/webp", "bmp", "image/bmp");
    private final OkHttpClient client;

    public RemoteArticleImageDownloader() {
        this(defaultClient());
    }

    RemoteArticleImageDownloader(OkHttpClient client) {
        this.client = client;
    }

    private static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .dns(publicDns(Dns.SYSTEM))
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(8))
                .callTimeout(Duration.ofSeconds(DOWNLOAD_TIMEOUT_SECONDS))
                .build();
    }

    /** 在大小、总耗时、格式及尺寸校验通过后返回现有上传流程可接收的文件。 */
    public MultipartFile download(String source, long maxBytes, List<String> allowedExtensions) throws IOException {
        HttpUrl url = validateUrl(source);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DOWNLOAD_TIMEOUT_SECONDS);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IOException("Image download deadline exceeded");
            }
            Request request = new Request.Builder().url(url)
                    .header("Accept", "image/png,image/jpeg,image/gif,image/webp,image/bmp")
                    .header("Accept-Encoding", "identity")
                    .header("User-Agent", "Chen404-Article-Image-Import/1.0").build();
            var call = client.newCall(request);
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS);
            try (Response response = call.execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    HttpUrl next = location == null ? null : url.resolve(location);
                    if (next == null || redirect == MAX_REDIRECTS) {
                        throw new BadRequestException("图片重定向无效或次数过多");
                    }
                    url = validateUrl(next.toString());
                    continue;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BadRequestException("图片来源暂不可访问，可重试或手动上传");
                }
                ResponseBody body = response.body();
                if (body.contentLength() > maxBytes) {
                    throw new BadRequestException("图片超过站点上传大小限制");
                }
                byte[] bytes = body.byteStream().readNBytes(Math.toIntExact(maxBytes + 1));
                if (bytes.length > maxBytes) {
                    throw new BadRequestException("图片超过站点上传大小限制");
                }
                return validateImage(bytes, allowedExtensions);
            }
        }
        throw new BadRequestException("图片重定向次数过多");
    }

    static HttpUrl validateUrl(String source) {
        HttpUrl url = source == null || source.length() > ArticleImageImportConstraints.URL_MAX_LENGTH ? null : HttpUrl.parse(source);
        if (url == null || !url.username().isEmpty() || !url.password().isEmpty()
                || url.port() != HttpUrl.defaultPort(url.scheme())) {
            throw new BadRequestException("仅支持不含账号密码、使用标准端口的 HTTP(S) 图片链接");
        }
        return url.newBuilder().fragment(null).build();
    }

    static Dns publicDns(Dns delegate) {
        return hostname -> {
            List<InetAddress> addresses = delegate.lookup(hostname);
            if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublicAddress(address))) {
                throw new UnknownHostException("Non-public image host rejected");
            }
            return addresses;
        };
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (bytes.length == 4) {
            int third = bytes[2] & 0xff;
            return first != 0 && first != 10 && first != 127 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && (second == 168 || (second == 0 && (third == 0 || third == 2))
                    || (second == 88 && third == 99)))
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        // 只接受全球单播；排除协议保留、文档及 6to4 隧道，拒绝 IPv4 映射/转换等特殊地址。
        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 0xfe) == 0)
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8)
                && !(first == 0x20 && second == 0x02)
                && !(first == 0x3f && (second & 0xf0) == 0xf0);
    }

    static MultipartFile validateImage(byte[] bytes, List<String> allowedExtensions) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("链接内容不是可识别的图片，支持 PNG、JPG、GIF、WebP、BMP");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String contentType = IMAGE_TYPES.get(format);
                boolean enabled = allowedExtensions == null || allowedExtensions.isEmpty()
                        || allowedExtensions.stream().anyMatch(extension -> format.equalsIgnoreCase(extension)
                        || (format.equals("jpeg") && "jpg".equalsIgnoreCase(extension)));
                if (contentType == null || !enabled) {
                    throw new BadRequestException("该图片格式不在站点允许范围内");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BadRequestException("图片尺寸过大，请缩小后手动上传");
                }
                return new DownloadedImage(bytes, format, contentType);
            } finally {
                reader.dispose();
            }
        }
    }

    /** 有界内存中的图片适配器；不把远程文件名或路径带入存储系统。 */
    private record DownloadedImage(byte[] data, String extension, String contentType) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return "imported-image." + extension; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), data); }
    }
}
