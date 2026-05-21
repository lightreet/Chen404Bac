package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminFileDetailVO;
import com.chen404.domain.dto.AdminFileVO;
import com.chen404.service.AdminFileService;
import com.chen404.service.FileReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "后台文件管理", description = "后台文件列表、详情与引用关系维护接口")
@RestController
@RequestMapping("/admin/files")
public class AdminFileController {

    private final AdminFileService adminFileService;
    private final FileReferenceService fileReferenceService;

    public AdminFileController(
            AdminFileService adminFileService,
            FileReferenceService fileReferenceService) {
        this.adminFileService = adminFileService;
        this.fileReferenceService = fileReferenceService;
    }

    @RequireAdmin
    @Operation(summary = "分页查询后台文件", description = "支持按关键字、状态、引用类型和是否已被引用筛选")
    @GetMapping
    public Result<PageResult<AdminFileVO>> getAdminFiles(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键字，匹配文件名、原始文件名或 URL")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "文件状态，如 TEMP / PERMANENT / DELETED")
            @RequestParam(required = false) String status,
            @Parameter(description = "上传归属类型，如 ARTICLE_CONTENT / AVATAR")
            @RequestParam(required = false) String refType,
            @Parameter(description = "是否已被引用")
            @RequestParam(required = false) Boolean referenced) {
        return Result.success(adminFileService.getAdminFiles(page, size, keyword, status, refType, referenced));
    }

    @RequireAdmin
    @Operation(summary = "查询文件详情", description = "返回文件基础信息和统一引用详情")
    @GetMapping("/{id}")
    public Result<AdminFileDetailVO> getAdminFileDetail(
            @Parameter(description = "文件 ID", required = true, example = "1001")
            @PathVariable Long id) {
        return Result.success(adminFileService.getAdminFileDetail(id));
    }

    @RequireAdmin
    @Operation(summary = "重建文件引用关系", description = "基于现有业务数据重新生成统一文件引用表")
    @PostMapping("/rebuild-references")
    public Result<Map<String, Integer>> rebuildReferences() {
        return Result.success("重建完成", fileReferenceService.rebuildAllReferences());
    }
}
