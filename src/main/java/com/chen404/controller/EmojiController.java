package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import com.chen404.service.EmojiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "表情包", description = "表情包公共下发接口")
@RestController
@RequestMapping("/emoji")
public class EmojiController {

    @Autowired
    private EmojiService emojiService;

    @Operation(summary = "获取表情包列表（启用）")
    @GetMapping("/packs")
    public Result<List<EmojiPack>> packs() {
        return Result.success(emojiService.listEnabledPacks());
    }

    @Operation(summary = "获取表情项列表（启用）", description = "可按 scene/packCode 过滤；scene 当前仅用于前端策略，不做后端过滤")
    @GetMapping("/items")
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

