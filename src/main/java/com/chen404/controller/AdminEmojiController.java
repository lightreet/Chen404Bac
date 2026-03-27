package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import com.chen404.service.EmojiImportService;
import com.chen404.service.EmojiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "管理员-表情包", description = "表情包管理与批量导入")
@RestController
@RequestMapping("/admin/emoji")
public class AdminEmojiController {

    @Autowired
    private EmojiService emojiService;

    @Autowired
    private EmojiImportService emojiImportService;

    @RequireAdmin
    @Operation(summary = "创建/更新表情包（按 packCode upsert）")
    @PostMapping("/packs")
    public Result<EmojiPack> upsertPack(@RequestBody EmojiPackUpsertDTO dto) {
        return Result.success(emojiService.upsertPack(dto));
    }

    @RequireAdmin
    @Operation(summary = "删除表情包")
    @DeleteMapping("/packs/{id}")
    public Result<Void> deletePack(@PathVariable Long id) {
        emojiService.deletePack(id);
        return Result.success("删除成功");
    }

    @RequireAdmin
    @Operation(summary = "创建/更新表情项（按 shortcode upsert）")
    @PostMapping("/items")
    public Result<EmojiItem> upsertItem(@RequestBody EmojiItemUpsertDTO dto) {
        return Result.success(emojiService.upsertItem(dto));
    }

    @RequireAdmin
    @Operation(summary = "删除表情项")
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        emojiService.deleteItem(id);
        return Result.success("删除成功");
    }

    @RequireAdmin
    @Operation(summary = "批量导入表情包（zip+manifest.json）")
    @PostMapping("/import")
    public Result<Map<String, Object>> importZip(@RequestParam("file") MultipartFile file) {
        return Result.success(emojiImportService.importZip(file));
    }
}

