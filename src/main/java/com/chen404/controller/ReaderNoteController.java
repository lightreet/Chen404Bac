package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.ReaderNoteCreateCommand;
import com.chen404.domain.dto.ReaderNoteUpdateCommand;
import com.chen404.domain.dto.ReaderNoteVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ReaderNoteService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录用户私有阅读笔记接口。
 *
 * <p>所有入口都强制绑定当前用户，笔记不会向书籍所有者、管理员或其他读者公开。</p>
 */
@Tag(name = "阅读笔记", description = "登录用户私有阅读笔记与原文定位")
@RestController
public class ReaderNoteController {

    private final ReaderNoteService readerNoteService;

    public ReaderNoteController(ReaderNoteService readerNoteService) {
        this.readerNoteService = readerNoteService;
    }

    @Operation(summary = "列出当前用户的书内笔记", description = "按章节和原文位置排序，仅返回当前登录用户的数据")
    @GetMapping("/reader/books/{bookId}/notes")
    public Result<List<ReaderNoteVO>> listNotes(
            @PathVariable Long bookId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(readerNoteService.listNotes(bookId, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "记录阅读笔记", description = "保存连续原文选区、可选感悟和低干扰高亮色")
    @PostMapping("/reader/books/{bookId}/notes")
    public Result<ReaderNoteVO> createNote(
            @PathVariable Long bookId,
            @Valid @RequestBody ReaderNoteCreateCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("阅读笔记已保存", readerNoteService.createNote(
                bookId, command, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "修改阅读笔记", description = "仅可修改本人笔记的感悟与高亮色")
    @PatchMapping("/reader/notes/{noteId}")
    public Result<ReaderNoteVO> updateNote(
            @PathVariable Long noteId,
            @Valid @RequestBody ReaderNoteUpdateCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success("阅读笔记已更新", readerNoteService.updateNote(
                noteId, command, CurrentUserUtil.requireUserId(currentUser)));
    }

    @Operation(summary = "删除阅读笔记", description = "永久删除当前用户自己的笔记")
    @DeleteMapping("/reader/notes/{noteId}")
    public Result<Void> deleteNote(
            @PathVariable Long noteId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        readerNoteService.deleteNote(noteId, CurrentUserUtil.requireUserId(currentUser));
        return Result.success("阅读笔记已删除");
    }
}
