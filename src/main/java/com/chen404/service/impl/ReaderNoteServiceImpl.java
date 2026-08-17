package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.converter.ReaderNoteConverter;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderChapterVO;
import com.chen404.domain.dto.ReaderNoteCreateCommand;
import com.chen404.domain.dto.ReaderNoteUpdateCommand;
import com.chen404.domain.dto.ReaderNoteVO;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.ReaderChapter;
import com.chen404.domain.entity.ReaderNote;
import com.chen404.domain.enums.ReaderNoteColorEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderNoteMapper;
import com.chen404.service.ReaderLibraryService;
import com.chen404.service.ReaderNoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 私有阅读笔记实现。
 *
 * <p>所有查询都绑定当前用户；列表只返回本人数据。原文锚点同时保留正文版本、章节哈希和上下文，
 * 由前端优先精确恢复，并在正文变化时执行摘录重定位。</p>
 */
@Slf4j
@Service
public class ReaderNoteServiceImpl implements ReaderNoteService {

    private final ReaderNoteMapper noteMapper;
    private final ReaderChapterMapper chapterMapper;
    private final ReaderLibraryService readerLibraryService;
    private final ReaderNoteConverter noteConverter;

    public ReaderNoteServiceImpl(
            ReaderNoteMapper noteMapper,
            ReaderChapterMapper chapterMapper,
            ReaderLibraryService readerLibraryService,
            ReaderNoteConverter noteConverter) {
        this.noteMapper = noteMapper;
        this.chapterMapper = chapterMapper;
        this.readerLibraryService = readerLibraryService;
        this.noteConverter = noteConverter;
    }

    @Override
    public List<ReaderNoteVO> listNotes(Long bookId, Long userId) {
        requireUser(userId);
        ReaderBookVO book = requireReadableBook(bookId, userId);
        List<ReaderNote> notes = noteMapper.selectList(new LambdaQueryWrapper<ReaderNote>()
                .eq(ReaderNote::getUserId, userId)
                .eq(ReaderNote::getBookId, bookId)
                .orderByAsc(ReaderNote::getChapterOrder)
                .orderByAsc(ReaderNote::getStartBlockIndex)
                .orderByAsc(ReaderNote::getStartCharacterOffset)
                .orderByAsc(ReaderNote::getId));
        if (notes.isEmpty()) {
            return List.of();
        }

        List<ReaderChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<ReaderChapter>()
                .select(
                        ReaderChapter::getId,
                        ReaderChapter::getBookId,
                        ReaderChapter::getChapterOrder,
                        ReaderChapter::getTitle,
                        ReaderChapter::getContentHash)
                .eq(ReaderChapter::getBookId, bookId));
        Map<Long, ReaderChapter> chapterById = new HashMap<>();
        Map<Integer, ReaderChapter> chapterByOrder = new HashMap<>();
        for (ReaderChapter chapter : chapters) {
            chapterById.put(chapter.getId(), chapter);
            chapterByOrder.putIfAbsent(chapter.getChapterOrder(), chapter);
        }
        return notes.stream()
                .map(note -> toLocatedVO(note, book, chapterById, chapterByOrder))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderNoteVO createNote(Long bookId, ReaderNoteCreateCommand command, Long userId) {
        requireUser(userId);
        validateRange(command);
        ReaderBookVO book = requireReadableBook(bookId, userId);
        if (!Objects.equals(command.getContentVersion(), book.getContentVersion())) {
            throw new BadRequestException("小说正文已更新，请重新选择原文后记录笔记");
        }
        ReaderChapterVO visibleChapter = readerLibraryService.getChapter(bookId, command.getChapterId(), userId);
        ReaderChapter chapter = chapterMapper.selectById(command.getChapterId());
        if (chapter == null || !Objects.equals(chapter.getBookId(), bookId)) {
            throw new ResourceNotFoundException("笔记对应章节不存在");
        }

        ReaderNote note = new ReaderNote();
        note.setUserId(userId);
        note.setBookId(bookId);
        note.setChapterId(chapter.getId());
        note.setChapterOrder(visibleChapter.getChapterOrder());
        note.setChapterTitle(visibleChapter.getTitle());
        note.setChapterContentHash(chapter.getContentHash());
        note.setStartBlockIndex(command.getStartBlockIndex());
        note.setStartCharacterOffset(command.getStartCharacterOffset());
        note.setEndBlockIndex(command.getEndBlockIndex());
        note.setEndCharacterOffset(command.getEndCharacterOffset());
        note.setExcerpt(command.getExcerpt());
        note.setReflection(blankToNull(command.getReflection()));
        note.setHighlightColor(ReaderNoteColorEnum.normalize(command.getHighlightColor()));
        note.setPrefixContext(blankToNull(command.getPrefixContext()));
        note.setSuffixContext(blankToNull(command.getSuffixContext()));
        note.setContentVersion(book.getContentVersion());
        noteMapper.insert(note);
        log.info("[READER_NOTE_CREATE] userId={} bookId={} chapterId={} noteId={}",
                userId, bookId, chapter.getId(), note.getId());
        return toCurrentVO(note, chapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReaderNoteVO updateNote(Long noteId, ReaderNoteUpdateCommand command, Long userId) {
        ReaderNote note = requireOwnedNote(noteId, userId);
        note.setReflection(blankToNull(command.getReflection()));
        note.setHighlightColor(ReaderNoteColorEnum.normalize(command.getHighlightColor()));
        noteMapper.updateById(note);

        ReaderBookVO book = requireReadableBook(note.getBookId(), userId);
        ReaderChapter currentChapter = resolveCurrentChapter(note);
        log.info("[READER_NOTE_UPDATE] userId={} bookId={} noteId={}", userId, note.getBookId(), noteId);
        return toLocatedVO(note, book, currentChapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNote(Long noteId, Long userId) {
        ReaderNote note = requireOwnedNote(noteId, userId);
        noteMapper.deleteById(noteId);
        log.info("[READER_NOTE_DELETE] userId={} bookId={} noteId={}", userId, note.getBookId(), noteId);
    }

    private ReaderBookVO requireReadableBook(Long bookId, Long userId) {
        ReaderBookVO book = readerLibraryService.getBook(bookId, userId);
        if (!ReaderBook.STATUS_READY.equals(book.getStatus())) {
            throw new BadRequestException("小说尚未导入完成");
        }
        return book;
    }

    private ReaderNote requireOwnedNote(Long noteId, Long userId) {
        requireUser(userId);
        ReaderNote note = noteId == null ? null : noteMapper.selectById(noteId);
        if (note == null || !Objects.equals(note.getUserId(), userId)) {
            log.warn("[READER_NOTE_NOT_FOUND] userId={} noteId={}", userId, noteId);
            throw new ResourceNotFoundException("阅读笔记不存在");
        }
        return note;
    }

    private void validateRange(ReaderNoteCreateCommand command) {
        int blockComparison = Integer.compare(command.getStartBlockIndex(), command.getEndBlockIndex());
        boolean reversed = blockComparison > 0
                || (blockComparison == 0
                && command.getStartCharacterOffset() >= command.getEndCharacterOffset());
        if (reversed) {
            throw new BadRequestException("笔记选区的结束位置必须在起始位置之后");
        }
        if (!StringUtils.hasText(command.getExcerpt())) {
            throw new BadRequestException("请选择要记录的原文");
        }
    }

    private ReaderChapter resolveCurrentChapter(ReaderNote note) {
        ReaderChapter current = chapterMapper.selectById(note.getChapterId());
        if (current != null && Objects.equals(current.getBookId(), note.getBookId())) {
            return current;
        }
        return chapterMapper.selectOne(new LambdaQueryWrapper<ReaderChapter>()
                .select(
                        ReaderChapter::getId,
                        ReaderChapter::getBookId,
                        ReaderChapter::getChapterOrder,
                        ReaderChapter::getTitle,
                        ReaderChapter::getContentHash)
                .eq(ReaderChapter::getBookId, note.getBookId())
                .eq(ReaderChapter::getChapterOrder, note.getChapterOrder())
                .last("LIMIT 1"));
    }

    private ReaderNoteVO toCurrentVO(ReaderNote note, ReaderChapter chapter) {
        ReaderNoteVO vo = noteConverter.toVO(note);
        vo.setTargetChapterId(chapter.getId());
        vo.setContentChanged(false);
        return vo;
    }

    private ReaderNoteVO toLocatedVO(
            ReaderNote note,
            ReaderBookVO book,
            Map<Long, ReaderChapter> chapterById,
            Map<Integer, ReaderChapter> chapterByOrder) {
        ReaderChapter chapter = chapterById.get(note.getChapterId());
        if (chapter == null) {
            chapter = chapterByOrder.get(note.getChapterOrder());
        }
        return toLocatedVO(note, book, chapter);
    }

    private ReaderNoteVO toLocatedVO(ReaderNote note, ReaderBookVO book, ReaderChapter chapter) {
        ReaderNoteVO vo = noteConverter.toVO(note);
        vo.setTargetChapterId(chapter == null ? null : chapter.getId());
        vo.setContentChanged(chapter == null
                || !Objects.equals(note.getContentVersion(), book.getContentVersion())
                || !Objects.equals(note.getChapterContentHash(), chapter.getContentHash()));
        return vo;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BadRequestException("请先登录后再记录阅读笔记");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
