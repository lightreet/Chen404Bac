package com.chen404.service;

import com.chen404.config.SiteRuntimeProperties;
import com.chen404.converter.TravelMobileUploadConverter;
import com.chen404.domain.dto.TravelMobileUploadDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.service.support.TravelMobileUploadSession;
import com.chen404.service.support.TravelMobileUploadSession.Item;
import com.chen404.service.support.TravelMobileUploadStore;
import com.chen404.service.support.SiteFrontendAddress;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.chen404.domain.enums.TravelMobilePhotoStatus.DONE;
import static com.chen404.domain.enums.TravelMobilePhotoStatus.FAILED;
import static com.chen404.domain.enums.TravelMobilePhotoStatus.UPLOADING;

/** 旅行手机直传授权与文件接收；匿名端仅获得指定槽位的短时上传能力。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TravelMobileUploadService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final Duration DESKTOP_LEASE = Duration.ofMinutes(2);
    private static final Duration UPLOAD_LEASE = Duration.ofMinutes(3);
    private static final int MAX_SESSION_PHOTOS = 60;
    private static final int MAX_PARALLEL_UPLOADS = 3;
    private static final int MAX_CREATES_PER_MINUTE = 10;
    private static final long MAX_IMAGE_PIXELS = 80_000_000L;
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_TEXT_LENGTH = 43;
    private static final int MAX_FILE_NAME_LENGTH = 120;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TravelMobileUploadStore store;
    private final AccessService accessService;
    private final RedisUtil redisUtil;
    private final SysFileService files;
    private final TravelMemoryImageMetadataService metadataService;
    private final SiteRuntimeProperties properties;
    private final ManagedFileUrlCodec fileUrls;
    private final SiteConfigService siteConfigService;
    private final TravelMobileUploadConverter converter;

    /** 只能由具备旅行创作权限的登录用户发起，不信任客户端传来的 ownerId。 */
    public TravelMobileUploadDTO.Created create(Long ownerId, TravelMobileUploadDTO.Create request) {
        ensureCreator(ownerId);
        String website = SiteFrontendAddress.forMobileUpload(siteConfigService.getConfig().getFrontendBaseUrl());
        Long attempts = redisUtil.incrementWithInitialTtl(RedisKeys.travelMobileCreateRate(ownerId), Duration.ofMinutes(1));
        if (attempts == null || attempts > MAX_CREATES_PER_MINUTE) {
            throw new BadRequestException("二维码生成过于频繁，请稍后重试");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        TravelMobileUploadSession session = new TravelMobileUploadSession();
        session.setId(UUID.randomUUID().toString());
        session.setOwnerId(ownerId);
        session.setTokenHash(hash(token));
        session.setTargetKind(request.targetKind());
        session.setTargetLabel(request.targetLabel());
        session.setTravelTitle(request.travelTitle());
        session.setExpiresAt(now + SESSION_TTL.toMillis());
        session.setDesktopSeenAt(now);
        session.setMaxCount("cover".equals(request.targetKind()) ? 1 : MAX_SESSION_PHOTOS);
        store.create(session);
        log.info("[TRAVEL_MOBILE_CREATE] userId={} targetKind={}", ownerId, request.targetKind());
        String uploadUrl = website + "/memory-map/mobile-upload#session=" + session.getId() + "&token=" + token;
        return new TravelMobileUploadDTO.Created(session.getId(), token, uploadUrl, snapshot(session, true));
    }

    /** 桌面轮询同时续租在线状态；已中断的会话不会因迟到的轮询恢复授权。 */
    public TravelMobileUploadDTO.Snapshot poll(String id, Long ownerId) {
        ensureCreator(ownerId);
        return snapshot(store.update(id, s -> {
            ensureOwner(s, ownerId);
            if (isActive(s)) s.setDesktopSeenAt(System.currentTimeMillis());
            expireAttempts(s);
        }), true);
    }

    /** 保存前 requireIdle=true 原子封口，保证完成图片全部进入最终表单。 */
    public TravelMobileUploadDTO.Snapshot close(String id, Long ownerId, boolean requireIdle) {
        return snapshot(store.update(id, s -> {
            ensureOwner(s, ownerId);
            expireAttempts(s);
            if (requireIdle && (uploadingCount(s) > 0 || isBatchActive(s))) {
                throw new BadRequestException("手机照片还在上传，请等待完成后再保存");
            }
            s.setClosed(true);
            s.setBatchId(null);
            s.setBatchExpiresAt(0);
        }), true);
    }

    public TravelMobileUploadDTO.Snapshot mobileStatus(String id, String token) {
        return mobileStatus(id, token, null);
    }

    /** 批次心跳仅更新批次租约，不延长二维码或桌面授权的有效期。 */
    public TravelMobileUploadDTO.Snapshot mobileStatus(String id, String token, String batchId) {
        TravelMobileUploadSession before = store.read(id);
        ensureToken(before, token);
        ensureCreator(before.getOwnerId());
        return snapshot(store.update(id, s -> {
            ensureToken(s, token);
            if (isActive(s)) s.setConnected(true);
            if (isActive(s) && isBatchActive(s) && Objects.equals(s.getBatchId(), batchId)) {
                s.setBatchExpiresAt(System.currentTimeMillis() + UPLOAD_LEASE.toMillis());
            }
            expireAttempts(s);
        }), false);
    }

    /** 锁定整批上传的保存边界，避免两次文件请求之间被桌面提前保存。 */
    public void beginBatch(String id, String token, String batchId) {
        validateRequestId(batchId);
        TravelMobileUploadSession before = store.read(id);
        ensureToken(before, token);
        ensureCreator(before.getOwnerId());
        store.update(id, s -> {
            ensureActive(s);
            if (isBatchActive(s) && !Objects.equals(s.getBatchId(), batchId)) {
                throw new BadRequestException("另一批照片还在上传，请等待完成后重试");
            }
            s.setBatchId(batchId);
            s.setBatchExpiresAt(System.currentTimeMillis() + UPLOAD_LEASE.toMillis());
        });
    }

    /** 即使批次已过期也允许释放；旧批次的迟到结束请求不能释放新批次。 */
    public void endBatch(String id, String token, String batchId) {
        validateRequestId(batchId);
        store.update(id, s -> {
            ensureToken(s, token);
            if (Objects.equals(s.getBatchId(), batchId)) {
                s.setBatchId(null);
                s.setBatchExpiresAt(0);
            }
        });
    }

    /** 每张图片使用固定 requestId 重试。先原子预占名额，再写入现有临时文件服务。 */
    public TravelMobileUploadDTO.Receipt upload(String id, String token, String requestId, MultipartFile file) {
        validateRequestId(requestId);
        TravelMobileUploadSession before = store.read(id);
        ensureToken(before, token);
        ensureActive(before);
        ensureCreator(before.getOwnerId());
        Item previous = before.getItems().get(requestId);
        if (previous != null && DONE.equals(previous.getStatus())) return receipt(requestId, previous);
        validateImage(file);
        String attemptId = UUID.randomUUID().toString();
        TravelMobileUploadSession reserved = store.update(id, s -> {
            ensureActive(s);
            expireAttempts(s);
            Item item = s.getItems().get(requestId);
            if (item != null && DONE.equals(item.getStatus())) return;
            if (item != null && UPLOADING.equals(item.getStatus())) {
                throw new BadRequestException("这张照片正在上传，请稍后重试查看结果");
            }
            if (uploadingCount(s) >= MAX_PARALLEL_UPLOADS) {
                throw new BadRequestException("正在接收其他照片，请稍后重试");
            }
            // 失败照片可被重新选择的文件替代；移除失败回执不会释放正在上传或已完成的名额。
            if (item == null) s.getItems().entrySet().removeIf(e -> FAILED.equals(e.getValue().getStatus()));
            if (item == null && s.getItems().size() >= s.getMaxCount()) {
                throw new BadRequestException("本次上传数量已达上限，请在电脑重新发起");
            }
            Item next = new Item();
            next.setName(safeName(file.getOriginalFilename()));
            next.setStartedAt(System.currentTimeMillis());
            next.setAttemptId(attemptId);
            next.setStatus(UPLOADING);
            s.getItems().put(requestId, next);
            s.setConnected(true);
        });
        Item item = reserved.getItems().get(requestId);
        if (DONE.equals(item.getStatus())) return receipt(requestId, item);
        try {
            var metadata = metadataService.extract(file);
            SysFile saved = files.uploadTempFile(file, reserved.getOwnerId(), SysFile.RefType.TRAVEL_MEMORY_IMAGE);
            UploadFileVO result = converter.fromFile(saved, metadata);
            result.setUrl(fileUrls.stableUrl(saved.getId()));
            // 关闭/过期后完成的存储仍为临时文件，由既有清理任务回收，不能回填到其他草稿。
            var completed = store.update(id, s -> {
                ensureActive(s);
                Item current = s.getItems().get(requestId);
                if (current == null || !UPLOADING.equals(current.getStatus()) || !Objects.equals(current.getAttemptId(), attemptId)) {
                    throw new BadRequestException("上传已重试，请等待最新结果");
                }
                current.setStatus(DONE);
                current.setResult(result);
            });
            log.info("[TRAVEL_MOBILE_UPLOAD_OK] userId={} fileId={}", reserved.getOwnerId(), saved.getId());
            return receipt(requestId, completed.getItems().get(requestId));
        } catch (RuntimeException ex) {
            try {
                store.update(id, s -> {
                    Item current = s.getItems().get(requestId);
                    if (current != null && Objects.equals(current.getAttemptId(), attemptId) && !DONE.equals(current.getStatus())) {
                        current.setStatus(FAILED);
                    }
                });
            } catch (RuntimeException cleanupError) {
                log.warn("[TRAVEL_MOBILE_RELEASE_FAIL] userId={}", reserved.getOwnerId());
            }
            log.warn("[TRAVEL_MOBILE_UPLOAD_FAIL] userId={} exception={}", reserved.getOwnerId(), ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private TravelMobileUploadDTO.Snapshot snapshot(TravelMobileUploadSession s, boolean desktop) {
        var receipts = s.getItems().entrySet().stream().map(e -> receipt(e.getKey(), e.getValue())).toList();
        List<UploadFileVO> images = desktop ? s.getItems().values().stream()
                .filter(i -> DONE.equals(i.getStatus())).map(Item::getResult)
                .map(this::issueImageView).toList() : List.of();
        return new TravelMobileUploadDTO.Snapshot(s.getId(), s.getTargetKind(), s.getTargetLabel(), s.getTravelTitle(),
                s.getExpiresAt(), s.isClosed() ? "closed" : isActive(s) ? "active" : "expired", s.isConnected(),
                uploadingCount(s), isBatchActive(s), s.getMaxCount(), properties.getUploadMaxSize(), properties.getUploadAllowTypes(), receipts, images);
    }

    private TravelMobileUploadDTO.Receipt receipt(String id, Item item) {
        return new TravelMobileUploadDTO.Receipt(id, item.getName(), item.getStatus().getCode());
    }

    private UploadFileVO issueImageView(UploadFileVO source) {
        UploadFileVO view = converter.copy(source);
        view.setUrl(fileUrls.ticketedUrl(view.getId()));
        return view;
    }

    private void expireAttempts(TravelMobileUploadSession s) {
        s.getItems().values().stream().filter(i -> UPLOADING.equals(i.getStatus())
                && System.currentTimeMillis() - i.getStartedAt() > UPLOAD_LEASE.toMillis())
                .forEach(i -> i.setStatus(FAILED));
    }

    private int uploadingCount(TravelMobileUploadSession s) {
        return (int) s.getItems().values().stream().filter(i -> UPLOADING.equals(i.getStatus())).count();
    }

    private boolean isBatchActive(TravelMobileUploadSession s) {
        return isActive(s) && s.getBatchId() != null && s.getBatchExpiresAt() > System.currentTimeMillis();
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || !requestId.matches("[a-zA-Z0-9-]{16,64}")) {
            throw new BadRequestException("上传请求无效，请重新选择照片");
        }
    }

    private boolean isActive(TravelMobileUploadSession s) {
        long now = System.currentTimeMillis();
        return !s.isClosed() && now < s.getExpiresAt() && now - s.getDesktopSeenAt() < DESKTOP_LEASE.toMillis();
    }

    private void ensureActive(TravelMobileUploadSession s) {
        if (!isActive(s)) throw new BadRequestException("上传已结束或电脑连接已中断，请在电脑重新生成二维码");
    }

    private void ensureCreator(Long ownerId) {
        if (!accessService.canCreateTravelMemory(ownerId)) throw new ForbiddenException("当前账号没有旅行图片上传权限");
    }

    private void ensureOwner(TravelMobileUploadSession s, Long ownerId) {
        if (!Objects.equals(s.getOwnerId(), ownerId)) throw new ForbiddenException("无权操作此上传会话");
    }

    private void ensureToken(TravelMobileUploadSession s, String token) {
        if (token == null || token.length() != TOKEN_TEXT_LENGTH || !MessageDigest.isEqual(
                s.getTokenHash().getBytes(StandardCharsets.US_ASCII), hash(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new ForbiddenException("上传链接无效，请重新扫描电脑上的二维码");
        }
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 不只信任 MIME 或扩展名，还验证实际图片头与像素上限，避免匿名凭证上传任意内容。 */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("请选择照片");
        if (file.getSize() > properties.getUploadMaxSize()) throw new BadRequestException("图片超过上传大小限制");
        try (var source = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("无法读取这张照片，请使用 JPG、PNG、GIF、WebP 或 BMP 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String normalized = "jpeg".equals(format) ? "jpg" : format;
                String mime = Objects.toString(file.getContentType(), "").toLowerCase(java.util.Locale.ROOT);
                String expectedMime = "jpg".equals(normalized) ? "image/jpeg" : "image/" + normalized;
                if (!expectedMime.equals(mime) && !("jpg".equals(normalized) && "image/jpg".equals(mime))) {
                    throw new BadRequestException("图片格式与文件类型不匹配，请从相册重新选择");
                }
                boolean allowed = properties.getUploadAllowTypes().stream()
                        .map(v -> v.toLowerCase(java.util.Locale.ROOT)).map(v -> "jpeg".equals(v) ? "jpg" : v)
                        .anyMatch(normalized::equals);
                if (!allowed) throw new BadRequestException("站点暂不支持这种图片格式");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_IMAGE_PIXELS) {
                    throw new BadRequestException("照片像素过大，请缩小后上传");
                }
            } finally {
                reader.dispose();
            }
        } catch (java.io.IOException ex) {
            throw new BadRequestException("照片已损坏或无法读取，请重新选择");
        }
    }

    private String safeName(String name) {
        String value = Objects.toString(name, "照片").replaceAll("[\\r\\n\\\\/]", "_");
        return value.substring(0, Math.min(MAX_FILE_NAME_LENGTH, value.length()));
    }
}
