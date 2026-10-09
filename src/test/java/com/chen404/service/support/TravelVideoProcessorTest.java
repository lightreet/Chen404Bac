package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelVideoProcessorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesStrictDurationAndActualVideoStream() throws Exception {
        assertEquals(29.999, TravelVideoProcessor.validateMetadata(mapper.readTree(metadata("29.999", "video", 1920))));
        for (String duration : new String[] { "30", "30.001", "0", "NaN", "Infinity" }) {
            assertThrows(BadRequestException.class,
                    () -> TravelVideoProcessor.validateMetadata(mapper.readTree(metadata(duration, "video", 1920))));
        }
        assertThrows(BadRequestException.class,
                () -> TravelVideoProcessor.validateMetadata(mapper.readTree(metadata("2", "audio", 1920))));
        assertThrows(BadRequestException.class,
                () -> TravelVideoProcessor.validateMetadata(mapper.readTree(metadata("2", "video", 8000))));
        assertThrows(BadRequestException.class, () -> TravelVideoProcessor.validateFile(
                new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[0])));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TRAVEL_FFMPEG_TEST_BIN", matches = ".+")
    void transcodesRealVideoAndRejectsLongOrDisguisedFiles(@TempDir Path directory) throws Exception {
        Path binaries = Path.of(System.getenv("TRAVEL_FFMPEG_TEST_BIN"));
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        String encoder = binaries.resolve("ffmpeg" + suffix).toString();
        TravelVideoProcessor processor = new TravelVideoProcessor(mapper, encoder, binaries.resolve("ffprobe" + suffix).toString());
        Path clip = directory.resolve("clip.mp4");
        generate(encoder, clip, "2");
        var result = processor.process(new MockMultipartFile("file", "clip.mp4", "video/mp4", Files.readAllBytes(clip)));
        assertEquals("video/mp4", result.video().getContentType());
        assertTrue(result.video().getSize() > 100);
        assertNotNull(javax.imageio.ImageIO.read(result.poster().getInputStream()));
        assertTrue(result.durationSeconds() > 0 && result.durationSeconds() < 30);
        var motion = TravelMotionPhotoReader.read(new MockMultipartFile("file", "live.jpg", "image/jpeg",
                MotionPhotoFixtures.join(MotionPhotoFixtures.jpeg(), Files.readAllBytes(clip), new byte[32])));
        assertNotNull(motion);
        var liveResult = processor.process(motion.video());
        assertTrue(liveResult.durationSeconds() > 0 && liveResult.durationSeconds() < 30);
        assertEquals(16, javax.imageio.ImageIO.read(motion.image().getInputStream()).getWidth());
        Path longClip = directory.resolve("long.mp4");
        generate(encoder, longClip, "30");
        var longMotion = TravelMotionPhotoReader.read(new MockMultipartFile("file", "long-live.jpg", "image/jpeg",
                MotionPhotoFixtures.join(MotionPhotoFixtures.jpeg(), Files.readAllBytes(longClip))));
        assertThrows(BadRequestException.class, () -> processor.process(longMotion.video()));
        assertThrows(BadRequestException.class, () -> processor.process(
                new MockMultipartFile("file", "long.mp4", "video/mp4", Files.readAllBytes(longClip))));
        assertThrows(BadRequestException.class, () -> processor.process(
                new MockMultipartFile("file", "fake.mp4", "video/mp4", "not a video".getBytes())));
    }

    private void generate(String encoder, Path target, String duration) throws Exception {
        Process process = new ProcessBuilder(encoder, "-y", "-v", "error", "-f", "lavfi", "-i",
                "testsrc2=size=160x90:rate=10", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                "-t", duration, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", target.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
    }

    private String metadata(String duration, String type, int width) {
        return "{\"format\":{\"duration\":\"" + duration + "\"},\"streams\":[{\"codec_type\":\""
                + type + "\",\"width\":" + width + ",\"height\":1080}]}";
    }
}
