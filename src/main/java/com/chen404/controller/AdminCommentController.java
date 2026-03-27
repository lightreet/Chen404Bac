package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ReviewCommentDTO;
import com.chen404.domain.entity.Comment;
import com.chen404.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "评论管理", description = "管理员审核评论")
@RestController
@RequestMapping("/admin/comments")
public class AdminCommentController {

    @Autowired
    private CommentService commentService;

    @Operation(summary = "审核评论", description = "通过或拒绝评论")
    @RequireAdmin
    @PutMapping("/{id}/review")
    public Result<Comment> reviewComment(
            @PathVariable Long id,
            @RequestBody ReviewCommentDTO dto) {
        Comment comment = commentService.reviewComment(id, dto.getStatus());
        return Result.success(comment);
    }
}
