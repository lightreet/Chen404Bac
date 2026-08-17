package com.chen404.service;

import com.chen404.domain.dto.ReaderNoteCreateCommand;
import com.chen404.domain.dto.ReaderNoteUpdateCommand;
import com.chen404.domain.dto.ReaderNoteVO;

import java.util.List;

/**
 * 用户私有阅读笔记服务。
 */
public interface ReaderNoteService {

    /**
     * 按章节和原文位置列出当前用户在指定书籍中的笔记。
     */
    List<ReaderNoteVO> listNotes(Long bookId, Long userId);

    /**
     * 为当前用户保存一段原文及可选感悟。
     */
    ReaderNoteVO createNote(Long bookId, ReaderNoteCreateCommand command, Long userId);

    /**
     * 修改当前用户笔记的感悟与高亮色，不改变原文锚点。
     */
    ReaderNoteVO updateNote(Long noteId, ReaderNoteUpdateCommand command, Long userId);

    /**
     * 永久删除当前用户自己的笔记。
     */
    void deleteNote(Long noteId, Long userId);
}
