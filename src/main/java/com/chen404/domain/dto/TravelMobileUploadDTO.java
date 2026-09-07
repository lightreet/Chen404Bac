package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 手机直传的请求、桌面回填与手机回执契约，手机回执不暴露文件访问地址。 */
public final class TravelMobileUploadDTO {
    private TravelMobileUploadDTO() { }

    @Schema(description = "已登录创作者发起手机直传")
    public record Create(
            @NotBlank @Pattern(regexp = "cover|stop") String targetKind,
            @NotBlank @Size(max = 80) String targetLabel,
            @Size(max = 50) String travelTitle) { }

    @Schema(description = "创建结果；token 仅此时返回，不是账号登录凭证")
    public record Created(String sessionId, String token, String uploadUrl, Snapshot session) { }

    @Schema(description = "手机端单张文件上传回执")
    public record Receipt(String requestId, String name, String status) { }

    @Schema(description = "短时上传会话；仅桌面所有者能收到 images")
    public record Snapshot(String sessionId, String targetKind, String targetLabel, String travelTitle,
                           long expiresAt, String status, boolean connected, int uploadingCount, boolean batchActive,
                           int maxCount, long maxFileBytes, List<String> allowedTypes,
                           List<Receipt> receipts, List<UploadFileVO> images) { }
}
