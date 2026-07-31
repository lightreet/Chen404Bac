package com.chen404.controller;

import com.chen404.converter.TagConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.TagVO;
import com.chen404.domain.entity.Tag;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签", description = "标签列表与标签详情接口")
@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;
    private final TagConverter tagConverter;

    public TagController(TagService tagService, TagConverter tagConverter) {
        this.tagService = tagService;
        this.tagConverter = tagConverter;
    }

    /**
     * 获取所有标签
     * @param withArticleCount 是否包含文章数量，默认为false
     */
    @Operation(summary = "获取所有标签", description = "返回启用中的标签列表，可选择是否附带文章数量字段")
    @GetMapping("")
    public Result<List<TagVO>> getTags(
            @Parameter(description = "是否包含文章数量，默认 false") @RequestParam(defaultValue = "false") boolean withArticleCount) {
        List<Tag> tags = tagService.getAllTags();
        // 如果为空，返回空列表而不是null
        if (tags == null) {
            tags = Collections.emptyList();
        }
        return Result.success(tagConverter.toVOList(tags));
    }

    @Operation(summary = "获取标签详情", description = "根据标签 ID 或 slug 获取单个标签详情")
    @GetMapping("/{idOrSlug}")
    public Result<TagVO> getTagById(
            @Parameter(description = "标签 ID 或 slug", required = true) @PathVariable String idOrSlug) {
        Tag tag = tagService.getTagByIdOrSlug(idOrSlug);
        if (tag == null) {
            throw new ResourceNotFoundException("标签不存在");
        }
        return Result.success(tagConverter.toVO(tag));
    }
}
