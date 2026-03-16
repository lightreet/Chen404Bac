package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Tag;
import com.chen404.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     */
    @GetMapping("")
    public Result<List<Tag>> getTags() {
        List<Tag> tags = tagService.getAllTags();
        return Result.success(tags);
    }
}
