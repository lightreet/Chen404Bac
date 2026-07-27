package com.chen404.service;

/**
 * 文件认领凭证。
 *
 * <p>同时提供文件 ID 与 URL 时，服务层必须验证两者指向同一条文件记录。
 * 仅有 URL 的旧业务结构仍可兼容，但 URL 只用于定位受管文件，不能绕过上传者校验。</p>
 */
public record FileClaim(Long fileId, String fileUrl) {

    public static FileClaim byUrl(String fileUrl) {
        return new FileClaim(null, fileUrl);
    }

    public static FileClaim byIdAndUrl(Long fileId, String fileUrl) {
        return new FileClaim(fileId, fileUrl);
    }
}
