package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.RemoteImageImportDTO;
import com.chen404.domain.dto.UploadFileVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ArticleImageImportService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 将文章图片转存到现有临时文件流程，不创建或发布文章。 */
@RestController
public class ArticleImageImportController {
    private final ArticleImageImportService imageImportService;

    public ArticleImageImportController(ArticleImageImportService imageImportService) {
        this.imageImportService = imageImportService;
    }

    @Operation(summary = "转存远程文章图片", description = "需要文章创作权限，成功返回临时文件，保存文章时认领")
    @PostMapping("/upload/image/import")
    public Result<UploadFileVO> importImage(
            @Valid @RequestBody RemoteImageImportDTO request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(imageImportService.importImage(request.getUrl(), userId));
    }
}
