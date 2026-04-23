package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import com.chen404.service.EmojiImportService;
import com.chen404.service.EmojiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Tag(name = "表情包", description = "表情包公共下发接口与管理员维护接口")
@RestController
public class EmojiController {

    @Autowired
    private EmojiService emojiService;

    @Autowired
    private EmojiImportService emojiImportService;

    @Operation(summary = "获取表情包列表（启用）")
    @GetMapping("/emoji/packs")
    public Result<List<EmojiPack>> packs() {
        return Result.success(emojiService.listEnabledPacks());
    }

    @Operation(summary = "获取表情项列表（启用）", description = "可按 scene/packCode 过滤；scene 当前仅用于前端策略，不做后端过滤")
    @GetMapping("/emoji/items")
    public Result<List<EmojiItem>> items(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String packCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        List<EmojiItem> list = emojiService.listEnabledItems(scene, packCode);
        String etag = buildEtag(list, scene, packCode);

        String ifNoneMatch = request.getHeader("If-None-Match");
        if (etag != null && etag.equals(ifNoneMatch)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return Result.success(List.of());
        }

        response.setHeader("ETag", etag);
        return Result.success(list);
    }

    @RequireAdmin
    @Operation(summary = "分页查询表情包", description = "仅管理员")
    @GetMapping("/admin/emoji/packs")
    public Result<PageResult<EmojiPack>> listPacks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        Page<EmojiPack> result = emojiService.pageAllPacks(page, size);
        return Result.success(PageResult.of(result));
    }

    @RequireAdmin
    @Operation(summary = "新增或更新表情包", description = "仅管理员")
    @PostMapping("/admin/emoji/packs")
    public Result<EmojiPack> upsertPack(@RequestBody EmojiPackUpsertDTO dto) {
        return Result.success(emojiService.upsertPack(dto));
    }

    @RequireAdmin
    @Operation(summary = "删除表情包", description = "仅管理员")
    @DeleteMapping("/admin/emoji/packs/{id}")
    public Result<Void> deletePack(@PathVariable Long id) {
        emojiService.deletePack(id);
        return Result.success("删除成功");
    }

    @RequireAdmin
    @Operation(summary = "分页查询表情项", description = "仅管理员")
    @GetMapping("/admin/emoji/items")
    public Result<PageResult<EmojiItem>> listItems(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) String packCode
    ) {
        Page<EmojiItem> result = emojiService.pageAllItems(page, size, packCode);
        return Result.success(PageResult.of(result));
    }

    @RequireAdmin
    @Operation(summary = "新增或更新表情项", description = "仅管理员")
    @PostMapping("/admin/emoji/items")
    public Result<EmojiItem> upsertItem(@RequestBody EmojiItemUpsertDTO dto) {
        return Result.success(emojiService.upsertItem(dto));
    }

    @RequireAdmin
    @Operation(summary = "删除表情项", description = "仅管理员")
    @DeleteMapping("/admin/emoji/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        emojiService.deleteItem(id);
        return Result.success("删除成功");
    }

    @RequireAdmin
    @Operation(summary = "导入表情包 ZIP", description = "仅管理员")
    @PostMapping("/admin/emoji/import")
    public Result<Map<String, Object>> importZip(@RequestParam("file") MultipartFile file) {
        return Result.success(emojiImportService.importZip(file));
    }

    private static String buildEtag(List<EmojiItem> list, String scene, String packCode) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("scene=").append(scene == null ? "" : scene).append(";pack=").append(packCode == null ? "" : packCode);
            sb.append(";count=").append(list.size());
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            list.stream()
                    .map(EmojiItem::getUpdateTime)
                    .filter(t -> t != null)
                    .max(java.time.LocalDateTime::compareTo)
                    .ifPresent(t -> sb.append(";max=").append(fmt.format(t)));

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "\"" + hex + "\"";
        } catch (Exception e) {
            return null;
        }
    }
}
