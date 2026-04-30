package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Tag;
import com.chen404.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/tags")
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 获取所有标签
     * @param withArticleCount 是否包含文章数量，默认为false
     */
    @GetMapping("")
    public Result<List<Tag>> getTags(
            @RequestParam(defaultValue = "false") boolean withArticleCount) {
        List<Tag> tags = tagService.getAllTags();
        // 如果为空，返回空列表而不是null
        if (tags == null) {
            tags = Collections.emptyList();
        }
        return Result.success(tags);
    }

    @Operation(summary = "获取标签详情")
    @GetMapping("/{idOrSlug}")
    public Result<Tag> getTagById(
            @Parameter(description = "标签 ID 或 slug", required = true) @PathVariable String idOrSlug) {
        Tag tag = tagService.getTagByIdOrSlug(idOrSlug);
        if (tag == null) {
            return Result.error(404, "标签不存在");
        }
        return Result.success(tag);
    }
}
