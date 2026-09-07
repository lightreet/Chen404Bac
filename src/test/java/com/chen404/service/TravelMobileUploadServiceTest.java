package com.chen404.service;

import com.chen404.config.PublicApiRequestMatcher;
import com.chen404.config.SiteRuntimeProperties;
import com.chen404.converter.TravelMobileUploadConverter;
import org.mapstruct.factory.Mappers;
import com.chen404.domain.dto.TravelMobileUploadDTO;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.TravelMobileUploadSession;
import com.chen404.service.support.TravelMobileUploadStore;
import com.chen404.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TravelMobileUploadServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MemoryStore store = new MemoryStore(mapper);
    private AccessService access;
    private SysFileService files;
    private SiteRuntimeProperties properties;
    private TravelMobileUploadService service;
    private SiteConfigService siteConfigService;
    private MockMultipartFile photo;
    private static final String REQUEST = "photo-request-0001";

    @BeforeEach
    void setup() throws Exception {
        access = mock(AccessService.class);
        files = mock(SysFileService.class);
        RedisUtil redis = mock(RedisUtil.class);
        properties = new SiteRuntimeProperties();
        when(access.canCreateTravelMemory(anyLong())).thenReturn(true);
        when(redis.incrementWithInitialTtl(anyString(), any())).thenReturn(1L);
        var metadata = mock(TravelMemoryImageMetadataService.class);
        when(metadata.extract(any())).thenReturn(new TravelMemoryImageMetadataService.TravelMemoryImageMetadata(BigDecimal.ONE, BigDecimal.TEN, null));
        siteConfigService = mock(SiteConfigService.class);
        var siteConfig = new com.chen404.domain.dto.SiteConfigDTO();
        siteConfig.setFrontendBaseUrl("https://www.chen404.cn");
        when(siteConfigService.getConfig()).thenReturn(siteConfig);
        service = new TravelMobileUploadService(store, access, redis, files, metadata, properties,
                new ManagedFileUrlCodec("test-only-mobile-upload-secret-key-32-bytes", 5), siteConfigService,
                Mappers.getMapper(TravelMobileUploadConverter.class));
        when(files.uploadTempFile(any(), anyLong(), anyString())).thenAnswer(call -> savedFile());
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", output);
        photo = new MockMultipartFile("file", "trip.png", "image/png", output.toByteArray());
    }

    @Test void requiresCreatorAndNeverStoresRawToken() {
        when(access.canCreateTravelMemory(2L)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.create(2L, request("stop")));
        var created = create("stop");
        assertEquals(43, created.token().length());
        assertNotEquals(created.token(), store.read(created.sessionId()).getTokenHash());
        assertEquals(60, created.session().maxCount());
        assertEquals("https://www.chen404.cn/memory-map/mobile-upload#session=" + created.sessionId() + "&token=" + created.token(), created.uploadUrl());
    }

    @Test void rejectsWrongTokenAndCrossOwnerPollingOrClosure() {
        var created = create("stop");
        assertThrows(ForbiddenException.class, () -> service.mobileStatus(created.sessionId(), "x".repeat(43)));
        assertThrows(ForbiddenException.class, () -> service.poll(created.sessionId(), 2L));
        assertThrows(ForbiddenException.class, () -> service.close(created.sessionId(), 2L, false));
        verifyNoInteractions(files);
    }

    @Test void newQrUsesLatestAdminWebsiteAddress() {
        var first = create("cover");
        var config = new com.chen404.domain.dto.SiteConfigDTO();
        config.setFrontendBaseUrl("https://updated.example.org/");
        when(siteConfigService.getConfig()).thenReturn(config);
        var second = create("cover");
        assertTrue(first.uploadUrl().startsWith("https://www.chen404.cn/"));
        assertTrue(second.uploadUrl().startsWith("https://updated.example.org/memory-map/mobile-upload#"));
        config.setFrontendBaseUrl("http://localhost:20204");
        assertThrows(BadRequestException.class, () -> create("cover"));
    }

    @Test void uploadIsIdempotentAndMobileCannotReadImageUrlsOrExif() {
        var created = create("stop");
        var first = service.upload(created.sessionId(), created.token(), REQUEST, photo);
        assertEquals(first, service.upload(created.sessionId(), created.token(), REQUEST, photo));
        verify(files, times(1)).uploadTempFile(photo, 1L, SysFile.RefType.TRAVEL_MEMORY_IMAGE);
        var mobile = service.mobileStatus(created.sessionId(), created.token());
        assertTrue(mobile.connected());
        assertTrue(mobile.images().isEmpty());
        var desktop = service.poll(created.sessionId(), 1L);
        assertEquals(1, desktop.images().size());
        assertEquals(BigDecimal.ONE, desktop.images().get(0).getLatitude());
        assertTrue(desktop.images().get(0).getUrl().startsWith("/api/files/101?ticket="));
    }

    @Test void rejectsSpoofedImageAndOversizeBeforeStorage() {
        var created = create("stop");
        var spoof = new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, spoof));
        properties.setUploadMaxSize(1);
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        verifyNoInteractions(files);
    }

    @Test void respectsConfiguredFormatRestrictions() {
        properties.setUploadAllowTypes(java.util.List.of("jpg"));
        var created = create("stop");
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        verifyNoInteractions(files);
    }

    @Test void validImageBytesCannotBeStoredWithExecutableContentType() throws Exception {
        var created = create("stop");
        var disguised = new MockMultipartFile("file", "trip.html", "text/html", photo.getBytes());
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, disguised));
        verifyNoInteractions(files);
    }

    @Test void failedStorageCanRetryWithoutOccupyingAnotherCoverSlot() {
        var created = create("cover");
        when(files.uploadTempFile(any(), anyLong(), anyString())).thenThrow(new IllegalStateException("test storage failure")).thenReturn(savedFile());
        assertThrows(IllegalStateException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        assertEquals("failed", service.mobileStatus(created.sessionId(), created.token()).receipts().get(0).status());
        assertEquals("done", service.upload(created.sessionId(), created.token(), REQUEST, photo).status());
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), "another-request-0002", photo));
    }

    @Test void aFailedCoverCanBeReplacedWithANewFileSelection() {
        var created = create("cover");
        when(files.uploadTempFile(any(), anyLong(), anyString())).thenThrow(new IllegalStateException("fail")).thenReturn(savedFile());
        assertThrows(IllegalStateException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        assertEquals("done", service.upload(created.sessionId(), created.token(), "another-request-0002", photo).status());
    }

    @Test void expiredSessionOrDesktopLeaseCannotBeRevivedByPolling() {
        var created = create("stop");
        store.update(created.sessionId(), s -> s.setDesktopSeenAt(0));
        assertEquals("expired", service.poll(created.sessionId(), 1L).status());
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        var second = create("cover");
        store.update(second.sessionId(), s -> s.setExpiresAt(0));
        assertEquals("expired", service.mobileStatus(second.sessionId(), second.token()).status());
    }

    @Test void closingPreservesDeliveredFilesAndRejectsFurtherUploads() {
        var created = create("stop");
        service.upload(created.sessionId(), created.token(), REQUEST, photo);
        var closed = service.close(created.sessionId(), 1L, true);
        assertEquals("closed", closed.status());
        assertEquals(1, closed.images().size());
        assertThrows(BadRequestException.class, () -> service.upload(created.sessionId(), created.token(), "another-request-0002", photo));
    }

    @Test void savingCannotRaceAnInFlightUploadAndRevocationDiscardsLateCompletion() throws Exception {
        var created = create("stop");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(files.uploadTempFile(any(), anyLong(), anyString())).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return savedFile(); });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> upload = executor.submit(() -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(BadRequestException.class, () -> service.close(created.sessionId(), 1L, true));
            assertEquals("active", service.poll(created.sessionId(), 1L).status());
            service.close(created.sessionId(), 1L, false);
            release.countDown();
            assertInstanceOf(BadRequestException.class, assertThrows(ExecutionException.class, () -> upload.get(5, TimeUnit.SECONDS)).getCause());
            assertTrue(service.poll(created.sessionId(), 1L).images().isEmpty());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void revokingCreatorPermissionAlsoRevokesMobileUpload() {
        var created = create("stop");
        when(access.canCreateTravelMemory(1L)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
        verifyNoInteractions(files);
    }

    @Test void removedTimedOutAttemptCannotPublishOrThrowNullPointerOnLateCompletion() throws Exception {
        var created = create("cover");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(files.uploadTempFile(any(), anyLong(), anyString())).thenAnswer(call -> {
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return savedFile();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> oldUpload = executor.submit(() -> service.upload(created.sessionId(), created.token(), REQUEST, photo));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            store.update(created.sessionId(), s -> s.getItems().get(REQUEST).setStartedAt(0));
            assertEquals("failed", service.mobileStatus(created.sessionId(), created.token()).receipts().get(0).status());
            assertEquals("done", service.upload(created.sessionId(), created.token(), "replacement-file-0002", photo).status());
            release.countDown();
            var failure = assertThrows(ExecutionException.class, () -> oldUpload.get(5, TimeUnit.SECONDS));
            assertInstanceOf(BadRequestException.class, failure.getCause());
            var snapshot = service.poll(created.sessionId(), 1L);
            assertEquals(1, snapshot.images().size());
            assertEquals("replacement-file-0002", snapshot.receipts().get(0).requestId());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void statusEnumRetainsExistingRedisJsonValues() throws Exception {
        var item = new TravelMobileUploadSession.Item();
        item.setStatus(com.chen404.domain.enums.TravelMobilePhotoStatus.UPLOADING);
        String json = mapper.writeValueAsString(item);
        assertTrue(json.contains("\"status\":\"uploading\""));
        assertEquals(item.getStatus(), mapper.readValue(json, TravelMobileUploadSession.Item.class).getStatus());
    }

    @Test void desktopSaveWaitsForWholeBatchEvenBetweenFileRequests() {
        var created = create("stop");
        String batch = "batch-request-0001";
        service.beginBatch(created.sessionId(), created.token(), batch);
        service.upload(created.sessionId(), created.token(), REQUEST, photo);
        var betweenFiles = service.poll(created.sessionId(), 1L);
        assertEquals(0, betweenFiles.uploadingCount());
        assertTrue(betweenFiles.batchActive());
        assertThrows(BadRequestException.class, () -> service.close(created.sessionId(), 1L, true));
        service.endBatch(created.sessionId(), created.token(), batch);
        assertEquals("closed", service.close(created.sessionId(), 1L, true).status());
    }

    @Test void expiredBatchAndStaleCompletionDoNotBlockOrReleaseNewBatch() {
        var created = create("stop");
        String first = "batch-request-0001", second = "batch-request-0002";
        service.beginBatch(created.sessionId(), created.token(), first);
        assertThrows(BadRequestException.class, () -> service.beginBatch(created.sessionId(), created.token(), second));
        store.update(created.sessionId(), s -> s.setBatchExpiresAt(0));
        service.beginBatch(created.sessionId(), created.token(), second);
        service.endBatch(created.sessionId(), created.token(), first);
        assertTrue(service.poll(created.sessionId(), 1L).batchActive());
        store.update(created.sessionId(), s -> s.setBatchExpiresAt(System.currentTimeMillis() + 1000));
        long authorizationExpiry = store.read(created.sessionId()).getExpiresAt();
        service.mobileStatus(created.sessionId(), created.token(), second);
        assertTrue(store.read(created.sessionId()).getBatchExpiresAt() > System.currentTimeMillis() + 60_000);
        assertEquals(authorizationExpiry, store.read(created.sessionId()).getExpiresAt());
        store.update(created.sessionId(), s -> s.setBatchExpiresAt(0));
        assertEquals("closed", service.close(created.sessionId(), 1L, true).status());
    }

    @Test void onlyExactPhoneRoutesBypassAccountAuthentication() {
        var matcher = new PublicApiRequestMatcher();
        String id = java.util.UUID.randomUUID().toString();
        assertTrue(matcher.matches(new MockHttpServletRequest("GET", "/api/upload/travel-mobile/public/" + id)));
        assertTrue(matcher.matches(new MockHttpServletRequest("POST", "/api/upload/travel-mobile/public/" + id + "/images")));
        assertFalse(matcher.matches(new MockHttpServletRequest("DELETE", "/api/upload/travel-mobile/public/" + id)));
        assertFalse(matcher.matches(new MockHttpServletRequest("POST", "/api/upload/travel-mobile/sessions")));
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/api/upload/travel-mobile/sessions/" + id)));
        String batchPath = "/api/upload/travel-mobile/public/" + id + "/batches/batch-request-0001";
        assertTrue(matcher.matches(new MockHttpServletRequest("POST", batchPath)));
        assertTrue(matcher.matches(new MockHttpServletRequest("DELETE", batchPath)));
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", batchPath)));
    }

    private TravelMobileUploadDTO.Create request(String kind) { return new TravelMobileUploadDTO.Create(kind, "第 1 站", "测试旅行"); }
    private TravelMobileUploadDTO.Created create(String kind) { return service.create(1L, request(kind)); }
    private SysFile savedFile() { SysFile f = new SysFile(); f.setId(101L); f.setFileName("trip.png"); f.setFileSize(70L); return f; }

    /** 测试替身保持 CAS 的副本提交语义，抛出异常的变更不能污染已提交会话。 */
    private static class MemoryStore extends TravelMobileUploadStore {
        private final Map<String, TravelMobileUploadSession> values = new java.util.HashMap<>();
        private final ObjectMapper mapper;
        MemoryStore(ObjectMapper mapper) { super(null, mapper); this.mapper = mapper; }
        @Override public synchronized void create(TravelMobileUploadSession s) { values.put(s.getId(), copy(s)); }
        @Override public synchronized TravelMobileUploadSession read(String id) { return copy(values.get(id)); }
        @Override public synchronized TravelMobileUploadSession update(String id, Consumer<TravelMobileUploadSession> change) {
            var copy = read(id); change.accept(copy); values.put(id, copy(copy)); return copy;
        }
        private TravelMobileUploadSession copy(TravelMobileUploadSession s) { return mapper.convertValue(s, TravelMobileUploadSession.class); }
    }
}
