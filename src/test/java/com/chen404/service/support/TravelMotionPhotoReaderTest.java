package com.chen404.service.support;

import com.chen404.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TravelMotionPhotoReaderTest {
    @Test
    void ordinaryImageAndStaticCopyStayImages() throws Exception {
        assertNull(TravelMotionPhotoReader.read(file(MotionPhotoFixtures.jpeg())));
        assertNull(TravelMotionPhotoReader.read(file(new byte[]{1, 2, 3})));
        assertNull(TravelMotionPhotoReader.read(null));
    }

    @Test
    void splitsJpegWithVideoAndVendorPaddingOrFooter() throws Exception {
        byte[] image = MotionPhotoFixtures.jpeg();
        byte[] video = MotionPhotoFixtures.container();
        for (byte[] original : new byte[][] {
                MotionPhotoFixtures.join(image, video),
                MotionPhotoFixtures.join(image, new byte[64], video, new byte[32]) }) {
            var result = TravelMotionPhotoReader.read(file(original));
            assertNotNull(result);
            assertArrayEquals(image, result.image().getBytes());
            assertArrayEquals(video, result.video().getBytes());
        }
    }

    @Test
    void ignoresEoiAndMovieSignaturesInsideJpegMetadata() throws Exception {
        byte[] image = MotionPhotoFixtures.jpeg();
        byte[] video = MotionPhotoFixtures.container();
        byte[] payload = MotionPhotoFixtures.join(new byte[]{(byte) 0xff, (byte) 0xd9}, video);
        int size = payload.length + 2;
        byte[] decorated = MotionPhotoFixtures.join(Arrays.copyOf(image, 2),
                new byte[]{(byte) 0xff, (byte) 0xe1, (byte) (size >> 8), (byte) size}, payload,
                Arrays.copyOfRange(image, 2, image.length));
        assertNull(TravelMotionPhotoReader.read(file(decorated)));
        var result = TravelMotionPhotoReader.read(file(MotionPhotoFixtures.join(decorated, video)));
        assertArrayEquals(decorated, result.image().getBytes());
    }

    @Test
    void corruptOrTruncatedMovieIsNotSilentlyFlattened() throws Exception {
        byte[] video = MotionPhotoFixtures.container();
        byte[] truncated = MotionPhotoFixtures.join(MotionPhotoFixtures.jpeg(), Arrays.copyOf(video, video.length - 1));
        assertThrows(BadRequestException.class, () -> TravelMotionPhotoReader.read(file(truncated)));
        video[0] = 0x7f;
        assertNull(TravelMotionPhotoReader.read(file(MotionPhotoFixtures.join(MotionPhotoFixtures.jpeg(), video))));
    }

    @Test
    void oversizeIsRejectedBeforeReadingTheBody() throws Exception {
        var oversized = new MockMultipartFile("file", "motion.jpg", "image/jpeg", MotionPhotoFixtures.jpeg()) {
            @Override public long getSize() { return TravelVideoProcessor.MAX_VIDEO_BYTES + 1; }
        };
        assertThrows(BadRequestException.class, () -> TravelMotionPhotoReader.read(oversized));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "motion.jpg", "image/jpeg", bytes);
    }
}
