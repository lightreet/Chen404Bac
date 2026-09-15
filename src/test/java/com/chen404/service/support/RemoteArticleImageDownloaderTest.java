package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteArticleImageDownloaderTest {
    @Test
    void rejectsLocalReservedAndTransitionAddresses() throws Exception {
        for (String address : List.of("0.0.0.0", "10.2.3.4", "127.0.0.1", "169.254.169.254", "172.16.0.1",
                "192.168.1.1", "100.64.0.1", "198.18.0.1", "192.0.2.1", "203.0.113.1", "224.0.0.1",
                "240.0.0.1", "::", "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "ff02::1",
                "64:ff9b::7f00:1", "2001::1", "2001:db8::1", "2002:7f00:1::1", "3fff::1")) {
            assertFalse(RemoteArticleImageDownloader.isPublicAddress(InetAddress.getByName(address)), address);
        }
        assertTrue(RemoteArticleImageDownloader.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(RemoteArticleImageDownloader.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void validatesDnsResultsAtConnectionResolutionIncludingMixedResponses() throws Exception {
        InetAddress publicIp = InetAddress.getByName("8.8.8.8");
        InetAddress localIp = InetAddress.getByName("127.0.0.1");
        Dns mixedDns = RemoteArticleImageDownloader.publicDns(host -> List.of(publicIp, localIp));
        assertThrows(UnknownHostException.class, () -> mixedDns.lookup("image.example"));
        AtomicInteger resolutions = new AtomicInteger();
        Dns rebindingDns = RemoteArticleImageDownloader.publicDns(host -> resolutions.getAndIncrement() == 0
                ? List.of(publicIp) : List.of(localIp));
        assertEquals(List.of(publicIp), rebindingDns.lookup("image.example"));
        assertThrows(UnknownHostException.class, () -> rebindingDns.lookup("image.example"));
    }

    @Test
    void rejectsUnsupportedProtocolsCredentialsPortsAndOversizedUrls() {
        for (String url : List.of("file:///etc/passwd", "ftp://example.com/image.png", "http://user:secret@example.com/a",
                "http://example.com:8080/a", "https://example.com/" + "a".repeat(4096))) {
            assertThrows(BadRequestException.class, () -> RemoteArticleImageDownloader.validateUrl(url));
        }
        assertEquals("https://example.com/a", RemoteArticleImageDownloader.validateUrl("https://example.com/a#fragment").toString());
    }

    @Test
    void detectsRealImageFormatAndRejectsHtmlSvgAndDisabledFormats() throws Exception {
        byte[] bytes = png();
        var file = RemoteArticleImageDownloader.validateImage(bytes, List.of("png"));
        assertArrayEquals(bytes, file.getBytes());
        assertEquals("image/png", file.getContentType());
        assertEquals("imported-image.png", file.getOriginalFilename());
        assertThrows(BadRequestException.class, () -> RemoteArticleImageDownloader.validateImage("<html>not an image</html>".getBytes(), List.of()));
        assertThrows(BadRequestException.class, () -> RemoteArticleImageDownloader.validateImage("<svg onload='alert(1)'/>".getBytes(), List.of()));
        assertThrows(BadRequestException.class, () -> RemoteArticleImageDownloader.validateImage(bytes, List.of("jpg")));
        assertThrows(BadRequestException.class, () -> RemoteArticleImageDownloader.validateImage(new byte[0], List.of()));
    }

    @Test
    void followsBoundedPublicRedirectAndReturnsValidatedBytes() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        byte[] bytes = png();
        var client = new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain -> {
            Response.Builder response = new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("test");
            if (requests.getAndIncrement() == 0) {
                return response.code(302).header("Location", "/final.png")
                        .body(ResponseBody.create(new byte[0], MediaType.get("text/plain"))).build();
            }
            assertEquals("/final.png", chain.request().url().encodedPath());
            assertEquals(null, chain.request().header("Authorization"));
            return response.code(200).body(ResponseBody.create(bytes, MediaType.get("image/png"))).build();
        }).build();
        var downloader = new RemoteArticleImageDownloader(client);
        assertArrayEquals(bytes, downloader.download("https://example.com/start", 1024, List.of("png")).getBytes());
        assertEquals(2, requests.get());
    }

    @Test
    void rejectsRedirectToPrivateHostBeforeConnecting() {
        AtomicInteger resolutions = new AtomicInteger();
        var client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false)
                .dns(RemoteArticleImageDownloader.publicDns(host -> {
                    resolutions.incrementAndGet();
                    return List.of(InetAddress.getByName("127.0.0.1"));
                }))
                .addInterceptor(chain -> {
                    if (chain.request().url().host().equals("example.com")) {
                        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("redirect")
                                .code(302).header("Location", "http://private.example/image")
                                .body(ResponseBody.create(new byte[0], MediaType.get("text/plain"))).build();
                    }
                    return chain.proceed(chain.request());
                }).build();
        assertThrows(IOException.class, () -> new RemoteArticleImageDownloader(client).download("http://example.com/image", 1024, List.of()));
        assertEquals(1, resolutions.get());
    }

    @Test
    void rejectsOversizedBodyAndRedirectLoops() throws Exception {
        byte[] bytes = png();
        var client = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).message("test").code(200)
                .body(ResponseBody.create(bytes, MediaType.get("image/png"))).build()).build();
        assertThrows(BadRequestException.class, () -> new RemoteArticleImageDownloader(client).download("https://example.com/image", 1, List.of()));
        AtomicInteger requests = new AtomicInteger();
        var loopClient = new OkHttpClient.Builder().followRedirects(false).addInterceptor(chain -> {
            requests.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("test").code(302)
                    .header("Location", "/again").body(ResponseBody.create(new byte[0], MediaType.get("text/plain"))).build();
        }).build();
        assertThrows(BadRequestException.class, () -> new RemoteArticleImageDownloader(loopClient).download("https://example.com/image", 1024, List.of()));
        assertEquals(4, requests.get());
    }

    private byte[] png() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
