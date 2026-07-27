package com.chen404.controller;

import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ProtectedFileAccessService;
import com.chen404.util.CurrentUserUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 受管文件访问入口。
 */
@RestController
@RequestMapping("/files")
public class FileAccessController {

    private final ProtectedFileAccessService protectedFileAccessService;

    public FileAccessController(ProtectedFileAccessService protectedFileAccessService) {
        this.protectedFileAccessService = protectedFileAccessService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Void> accessFile(
            @PathVariable Long id,
            @RequestParam(required = false) String ticket,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        String downloadUrl = protectedFileAccessService.resolveDownloadUrl(
                id,
                CurrentUserUtil.getUserId(currentUser),
                ticket
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .location(URI.create(downloadUrl))
                .build();
    }
}
