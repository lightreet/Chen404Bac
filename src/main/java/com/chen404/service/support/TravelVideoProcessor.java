package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 校验真实视频流并转为浏览器通用的 MP4，同时提取静态封面；不接受远程输入。 */
@Component
@Slf4j
public class TravelVideoProcessor {
    public static final long MAX_VIDEO_BYTES = 60L * 1024 * 1024;
    public static final double MAX_DURATION_SECONDS = 30;
    private static final int PROCESS_TIMEOUT_SECONDS = 90;
    private static final int MAX_INPUT_EDGE = 4096;
    private final Semaphore processingSlot = new Semaphore(1);
    private final ObjectMapper mapper;
    private final String ffmpeg;
    private final String ffprobe;

    public TravelVideoProcessor(ObjectMapper mapper,
            @Value("${app.travel-video.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${app.travel-video.ffprobe:ffprobe}") String ffprobe) {
        this.mapper = mapper;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    /** 入口格式预检，真正时长、格式和尺寸由探测器校验，不能依赖浏览器 metadata。 */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择视频文件");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BadRequestException("视频不能超过 60 MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|webm)$")) {
            throw new BadRequestException("请选择 MP4、MOV 或 WebM 视频");
        }
    }

    /** 并发、执行时间、输入输出大小均有界；临时目录在成功和失败时均回收。 */
    public ProcessedVideo process(MultipartFile file) {
        validateFile(file);
        if (!processingSlot.tryAcquire()) {
            throw new BadRequestException("正在处理其他视频，请稍后重试");
        }
        Path directory = null;
        try {
            directory = Files.createTempDirectory("chen404-travel-video-");
            Path input = directory.resolve("input");
            file.transferTo(input);
            double duration = probe(input, directory.resolve("probe.json"));
            Path video = directory.resolve("video.mp4");
            run(List.of(ffmpeg, "-nostdin", "-v", "error", "-y", "-protocol_whitelist", "file,pipe",
                    "-format_whitelist", "mov,matroska,webm", "-threads", "2", "-i", input.toString(),
                    "-map", "0:v:0", "-map", "0:a:0?", "-map_metadata", "-1", "-map_chapters", "-1",
                    "-vf", "scale=w='min(1280,iw)':h='min(1280,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1",
                    "-c:v", "libx264", "-threads", "2", "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p",
                    "-r", "30", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-t", Double.toString(MAX_DURATION_SECONDS),
                    "-fs", Long.toString(MAX_VIDEO_BYTES), video.toString()), null);
            // 转码后的时间基可能有变化；严格保持少于 30 秒，而不是悄悄截断超长输入。
            probe(video, directory.resolve("output-probe.json"));
            Path poster = directory.resolve("poster.jpg");
            run(List.of(ffmpeg, "-nostdin", "-v", "error", "-y", "-protocol_whitelist", "file,pipe",
                    "-i", video.toString(), "-frames:v", "1", "-q:v", "3", poster.toString()), null);
            return new ProcessedVideo(readMedia(video, "video/mp4"), readMedia(poster, "image/jpeg"), duration);
        } catch (IOException ex) {
            log.warn("[TRAVEL_VIDEO_PROCESS_IO] exception={}", ex.getClass().getSimpleName());
            throw new IllegalStateException("视频处理暂不可用，请稍后重试", ex);
        } finally {
            cleanup(directory);
            processingSlot.release();
        }
    }

    private double probe(Path input, Path output) throws IOException {
        run(List.of(ffprobe, "-v", "error", "-protocol_whitelist", "file,pipe", "-format_whitelist", "mov,matroska,webm",
                "-show_entries", "format=duration:stream=codec_type,width,height,duration", "-of", "json", input.toString()), output);
        if (Files.size(output) > 64 * 1024) {
            throw new BadRequestException("视频包含过多媒体轨道");
        }
        return validateMetadata(mapper.readTree(output.toFile()));
    }

    static double validateMetadata(JsonNode metadata) {
        double duration = metadata.path("format").path("duration").asDouble(Double.NaN);
        boolean hasVideo = false;
        for (JsonNode stream : metadata.path("streams")) {
            duration = Math.max(duration, stream.path("duration").asDouble(0));
            if ("video".equals(stream.path("codec_type").asText())) {
                int width = stream.path("width").asInt();
                int height = stream.path("height").asInt();
                if (width <= 0 || height <= 0 || width > MAX_INPUT_EDGE || height > MAX_INPUT_EDGE || (long) width * height > 9_000_000) {
                    throw new BadRequestException("视频分辨率过高，请压缩至 4K 或以下");
                }
                hasVideo = true;
            }
        }
        if (!hasVideo || !Double.isFinite(duration) || duration <= 0) {
            throw new BadRequestException("无法读取有效视频，请重新导出后上传");
        }
        if (duration >= MAX_DURATION_SECONDS) {
            throw new BadRequestException("视频时长须少于 30 秒，请裁剪后上传");
        }
        return duration;
    }

    private void run(List<String> command, Path output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()));
        Process process = builder.start();
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[TRAVEL_VIDEO_PROCESS_TIMEOUT] stage={}", command.get(0).equals(ffprobe) ? "probe" : "transcode");
                throw new BadRequestException("视频处理超时，请压缩后重试");
            }
            if (process.exitValue() != 0) {
                log.warn("[TRAVEL_VIDEO_PROCESS_FAILED] stage={} exitCode={}",
                        command.get(0).equals(ffprobe) ? "probe" : "transcode", process.exitValue());
                throw new BadRequestException("视频无法解码，请重新导出为 MP4 后上传");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("视频处理已中断", ex);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private MediaFile readMedia(Path path, String contentType) throws IOException {
        long size = Files.size(path);
        if (size == 0 || size >= MAX_VIDEO_BYTES) {
            throw new BadRequestException("视频处理结果过大，请压缩后重试");
        }
        return new MediaFile(path.getFileName().toString(), contentType, Files.readAllBytes(path));
    }

    private void cleanup(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            List<Path> files;
            try (var paths = Files.list(directory)) {
                files = paths.toList();
            }
            for (Path path : files) {
                Files.deleteIfExists(path);
            }
            Files.deleteIfExists(directory);
        } catch (IOException ex) {
            log.warn("[TRAVEL_VIDEO_TEMP_CLEANUP] exception={}", ex.getClass().getSimpleName());
        }
    }

    public record ProcessedVideo(MediaFile video, MediaFile poster, double durationSeconds) { }

    /** 已验证并转码的内存文件，复用系统上传与临时文件生命周期。 */
    public record MediaFile(String filename, String contentType, byte[] data) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), data); }
    }
}
