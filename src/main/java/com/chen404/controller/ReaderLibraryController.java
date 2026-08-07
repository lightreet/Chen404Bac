package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.ReaderBookUpdateCommand;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderBookPreviewVO;
import com.chen404.domain.dto.ReaderChapterVO;
import com.chen404.domain.dto.ReaderPreferenceCommand;
import com.chen404.domain.dto.ReaderPreferenceVO;
import com.chen404.domain.dto.ReaderProgressCommand;
import com.chen404.domain.dto.ReaderProgressVO;
import com.chen404.domain.dto.ReaderSearchResultVO;
import com.chen404.domain.dto.ReaderTocItemVO;
import com.chen404.exception.ForbiddenException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AccessService;
import com.chen404.service.ReaderLibraryService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 公开书架与沉浸式阅读控制器。
 *
 * <p>公开书籍可匿名浏览和阅读；导入、编辑、删除、进度与偏好仍只对登录用户开放。</p>
 */
@Tag(name = "小说阅读", description = "小说导入、目录、正文、阅读进度与阅读偏好")
@Slf4j
@RestController
public class ReaderLibraryController {

    private final ReaderLibraryService readerLibraryService;
    private final AccessService accessService;

    public ReaderLibraryController(
            ReaderLibraryService readerLibraryService,
            AccessService accessService) {
        this.readerLibraryService = readerLibraryService;
        this.accessService = accessService;
    }

    @Operation(summary = "导入小说", description = "支持 TXT、EPUB、HTML、Markdown 与 FB2")
    @PostMapping(value = "/reader/books/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ReaderBookVO> importBook(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String encoding,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) Long coverFileId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureReaderBookImportPermission(userId);
        return Result.success("小说已导入书架",
                readerLibraryService.importBook(file, title, author, description, encoding, visibility, coverFileId, userId));
    }

    @Operation(summary = "预解析小说资料", description = "读取书名、作者、简介、语言和内嵌封面，供导入表单自动回填")
    @PostMapping(value = "/reader/books/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ReaderBookPreviewVO> previewBook(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String encoding,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        ensureReaderBookImportPermission(userId);
        return Result.success(readerLibraryService.previewBook(file, encoding, userId));
    }

    @Operation(summary = "获取公开书架", description = "匿名访客仅看到公开书籍；登录用户还会看到自己的私密书籍。")
    @GetMapping("/reader/books")
    public Result<List<ReaderBookVO>> listBooks(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.listBooks(CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "获取书籍详情")
    @GetMapping("/reader/books/{bookId}")
    public Result<ReaderBookVO> getBook(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.getBook(bookId, CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "修改书籍信息")
    @PatchMapping("/reader/books/{bookId}")
    public Result<ReaderBookVO> updateBook(
            @PathVariable Long bookId,
            @Valid @RequestBody ReaderBookUpdateCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("书籍信息已更新",
                readerLibraryService.updateBook(bookId, command, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "从书架删除小说")
    @DeleteMapping("/reader/books/{bookId}")
    public Result<Void> deleteBook(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        readerLibraryService.deleteBook(bookId, CurrentUserUtil.requireUserId(currentUser));
        return Result.success("小说已从书架删除");
    }

    @Operation(summary = "获取完整多级目录")
    @GetMapping("/reader/books/{bookId}/toc")
    public Result<List<ReaderTocItemVO>> getToc(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.getToc(bookId, CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "读取章节正文")
    @GetMapping("/reader/books/{bookId}/chapters/{chapterId}")
    public Result<ReaderChapterVO> getChapter(
            @PathVariable Long bookId,
            @PathVariable Long chapterId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.getChapter(
                bookId, chapterId, CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "搜索当前书籍")
    @GetMapping("/reader/books/{bookId}/search")
    public Result<List<ReaderSearchResultVO>> search(
            @PathVariable Long bookId,
            @RequestParam String keyword,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.search(
                bookId, keyword, CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "获取阅读进度")
    @GetMapping("/reader/books/{bookId}/progress")
    public Result<ReaderProgressVO> getProgress(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.getProgress(
                bookId, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "保存阅读进度")
    @PutMapping("/reader/books/{bookId}/progress")
    public Result<ReaderProgressVO> saveProgress(
            @PathVariable Long bookId,
            @Valid @RequestBody ReaderProgressCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("阅读进度已保存", readerLibraryService.saveProgress(
                bookId, command, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "清除阅读进度")
    @DeleteMapping("/reader/books/{bookId}/progress")
    public Result<Void> clearProgress(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        readerLibraryService.clearProgress(bookId, CurrentUserUtil.requireUserId(currentUser));
        return Result.success("阅读进度已清除");
    }

    @Operation(summary = "获取阅读偏好")
    @GetMapping("/reader/preferences")
    public Result<ReaderPreferenceVO> getPreference(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerLibraryService.getPreference(CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "保存阅读偏好")
    @PutMapping("/reader/preferences")
    public Result<ReaderPreferenceVO> savePreference(
            @Valid @RequestBody ReaderPreferenceCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("阅读设置已保存",
                readerLibraryService.savePreference(command, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "读取书籍内嵌图片")
    @GetMapping("/reader/books/{bookId}/assets/{assetId}")
    public ResponseEntity<byte[]> getAsset(
            @PathVariable Long bookId,
            @PathVariable Long assetId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ReaderLibraryService.ReaderAssetPayload asset = readerLibraryService.getAsset(
                bookId, assetId, CurrentUserUtil.getUserId(currentUser));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(asset.mediaType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(asset.fileName() == null ? "book-asset" : asset.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(asset.publicVisible()
                ? CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic()
                : CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy",
                "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:");
        return ResponseEntity.ok().headers(headers).body(asset.data());
    }

    /**
     * 预解析和导入必须保持同一权限边界，防止普通登录用户绕过前端占用小说资源。
     */
    private void ensureReaderBookImportPermission(Long userId) {
        if (!accessService.canImportReaderBook(userId)) {
            log.warn("[READER_BOOK_IMPORT_FORBIDDEN] userId={}", userId);
            throw new ForbiddenException("仅知友或管理员可导入小说");
        }
    }

}
