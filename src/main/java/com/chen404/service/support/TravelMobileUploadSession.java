package com.chen404.service.support;

import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.enums.TravelMobilePhotoStatus;
import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

/** 仅在 Redis 中存储的上传授权状态；凭证只保存 SHA-256 摘要。 */
@Data
public class TravelMobileUploadSession {
    private String id;
    private Long ownerId;
    private String tokenHash;
    private String targetKind;
    private String targetLabel;
    private String travelTitle;
    private long expiresAt;
    private long desktopSeenAt;
    private boolean closed;
    private boolean connected;
    private String batchId;
    private long batchExpiresAt;
    private int maxCount;
    private Map<String, Item> items = new LinkedHashMap<>();

    /** 同一 requestId 的重试共用一个名额和结果，防止弱网重复入库。 */
    @Data
    public static class Item {
        private String name;
        private long startedAt;
        private String attemptId;
        private TravelMobilePhotoStatus status;
        private UploadFileVO result;
    }
}
