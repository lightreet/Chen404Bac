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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "表情包管理", description = "管理员：表情包与表情项维护、ZIP 导入")
@RestController
@RequestMapping("/admin/emoji")
public class AdminEmojiController {

    @Autowired
    private EmojiService emojiService;

    @Autowired
    private EmojiImportService emojiImportService;

    @RequireAdmin
    @Operation(summary = "分页查询表情包")
    @GetMapping("/packs")
    public Result<PageResult<EmojiPack>> listPacks(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        Page<EmojiPack> result = emojiService.pageAllPacks(page, size);
        return Result.success(PageResult.of(result));
    }

    @RequireAdmin
    @Operation(summary = "新增或更新表情包")
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
    @Operation(summary = "分页查询表情项")
    @GetMapping("/items")
    public Result<PageResult<EmojiItem>> listItems(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) String packCode
    ) {
        Page<EmojiItem> result = emojiService.pageAllItems(page, size, packCode);
        return Result.success(PageResult.of(result));
    }

    @RequireAdmin
    @Operation(summary = "新增或更新表情项")
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
    @Operation(summary = "导入表情包 ZIP")
    @PostMapping("/import")
    public Result<Map<String, Object>> importZip(@RequestParam("file") MultipartFile file) {
        return Result.success(emojiImportService.importZip(file));
    }
}
