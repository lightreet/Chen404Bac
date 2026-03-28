package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "归档", description = "按年月聚合的公开已发布文章")
@RestController
@RequestMapping("/archives")
public class ArchiveController {

    @Autowired
    private ArticleService articleService;

    @Operation(summary = "归档时间线", description = "仅包含已发布且公开可见、有发布时间的文章，按发布时间倒序分组")
    @GetMapping("")
    public Result<List<ArchiveYearVO>> listArchives() {
        return Result.success(articleService.listArchives());
    }
}
