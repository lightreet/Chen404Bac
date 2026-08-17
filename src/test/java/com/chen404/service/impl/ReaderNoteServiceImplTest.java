package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.converter.ReaderNoteConverter;
import com.chen404.domain.dto.ReaderBookVO;
import com.chen404.domain.dto.ReaderChapterVO;
import com.chen404.domain.dto.ReaderNoteCreateCommand;
import com.chen404.domain.dto.ReaderNoteUpdateCommand;
import com.chen404.domain.dto.ReaderNoteVO;
import com.chen404.domain.entity.ReaderBook;
import com.chen404.domain.entity.ReaderChapter;
import com.chen404.domain.entity.ReaderNote;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.ReaderChapterMapper;
import com.chen404.mapper.ReaderNoteMapper;
import com.chen404.service.ReaderLibraryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阅读笔记私有边界、可选感悟和正文变化定位测试。
 */
class ReaderNoteServiceImplTest {

    @Test
    void shouldCreateExcerptOnlyNoteWithoutReflection() {
        ReaderNoteMapper noteMapper = mock(ReaderNoteMapper.class);
        ReaderChapterMapper chapterMapper = mock(ReaderChapterMapper.class);
        ReaderLibraryService libraryService = mock(ReaderLibraryService.class);
        when(libraryService.getBook(7L, 12L)).thenReturn(book(7L, 1));
        when(libraryService.getChapter(7L, 21L, 12L)).thenReturn(chapterVO(21L, 0, "序章"));
        when(chapterMapper.selectById(21L)).thenReturn(chapter(21L, 7L, 0, "序章", "hash-a"));
        doAnswer(invocation -> {
            ReaderNote note = invocation.getArgument(0);
            note.setId(31L);
            note.setCreateTime(LocalDateTime.now());
            note.setUpdateTime(LocalDateTime.now());
            return 1;
        }).when(noteMapper).insert(any(ReaderNote.class));

        ReaderNoteVO result = service(noteMapper, chapterMapper, libraryService)
                .createNote(7L, createCommand(), 12L);

        assertEquals(31L, result.getId());
        assertEquals(21L, result.getTargetChapterId());
        assertEquals("rose", result.getHighlightColor());
        assertNull(result.getReflection());
        assertEquals(false, result.getContentChanged());
    }

    @Test
    void shouldFilterListByCurrentUserAndFallbackToCurrentChapterOrder() {
        initTableInfo(ReaderNote.class);
        initTableInfo(ReaderChapter.class);
        ReaderNoteMapper noteMapper = mock(ReaderNoteMapper.class);
        ReaderChapterMapper chapterMapper = mock(ReaderChapterMapper.class);
        ReaderLibraryService libraryService = mock(ReaderLibraryService.class);
        ReaderNote note = note(31L, 12L, 7L, 21L, 0, "old-hash", 1);
        ReaderChapter currentChapter = chapter(41L, 7L, 0, "序章（修订）", "new-hash");
        when(libraryService.getBook(7L, 12L)).thenReturn(book(7L, 2));
        when(noteMapper.selectList(any())).thenReturn(List.of(note));
        when(chapterMapper.selectList(any())).thenReturn(List.of(currentChapter));

        List<ReaderNoteVO> result = service(noteMapper, chapterMapper, libraryService).listNotes(7L, 12L);

        ArgumentCaptor<LambdaQueryWrapper<ReaderNote>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(noteMapper).selectList(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("book_id"));
        assertTrue(sql.contains("order by"));
        assertEquals(41L, result.get(0).getTargetChapterId());
        assertEquals(true, result.get(0).getContentChanged());
    }

    @Test
    void shouldHideOtherUsersNoteOnUpdate() {
        ReaderNoteMapper noteMapper = mock(ReaderNoteMapper.class);
        ReaderNote otherUsersNote = note(31L, 99L, 7L, 21L, 0, "hash-a", 1);
        when(noteMapper.selectById(31L)).thenReturn(otherUsersNote);
        ReaderNoteUpdateCommand command = new ReaderNoteUpdateCommand();
        command.setReflection("新的感悟");
        command.setHighlightColor("blue");

        assertThrows(ResourceNotFoundException.class,
                () -> service(noteMapper, mock(ReaderChapterMapper.class), mock(ReaderLibraryService.class))
                        .updateNote(31L, command, 12L));
        verify(noteMapper, never()).updateById(any(ReaderNote.class));
    }

    @Test
    void shouldRejectRangeWhoseEndDoesNotFollowStart() {
        ReaderNoteCreateCommand command = createCommand();
        command.setEndBlockIndex(command.getStartBlockIndex());
        command.setEndCharacterOffset(command.getStartCharacterOffset());

        assertThrows(BadRequestException.class,
                () -> service(mock(ReaderNoteMapper.class), mock(ReaderChapterMapper.class), mock(ReaderLibraryService.class))
                        .createNote(7L, command, 12L));
    }

    @Test
    void shouldRejectSelectionCapturedFromStaleContentVersion() {
        ReaderNoteMapper noteMapper = mock(ReaderNoteMapper.class);
        ReaderLibraryService libraryService = mock(ReaderLibraryService.class);
        when(libraryService.getBook(7L, 12L)).thenReturn(book(7L, 2));

        assertThrows(BadRequestException.class,
                () -> service(noteMapper, mock(ReaderChapterMapper.class), libraryService)
                        .createNote(7L, createCommand(), 12L));
        verify(noteMapper, never()).insert(any(ReaderNote.class));
    }

    private ReaderNoteServiceImpl service(
            ReaderNoteMapper noteMapper,
            ReaderChapterMapper chapterMapper,
            ReaderLibraryService libraryService) {
        return new ReaderNoteServiceImpl(noteMapper, chapterMapper, libraryService, converter());
    }

    private ReaderNoteConverter converter() {
        return note -> {
            ReaderNoteVO vo = new ReaderNoteVO();
            vo.setId(note.getId());
            vo.setBookId(note.getBookId());
            vo.setChapterId(note.getChapterId());
            vo.setChapterOrder(note.getChapterOrder());
            vo.setChapterTitle(note.getChapterTitle());
            vo.setStartBlockIndex(note.getStartBlockIndex());
            vo.setStartCharacterOffset(note.getStartCharacterOffset());
            vo.setEndBlockIndex(note.getEndBlockIndex());
            vo.setEndCharacterOffset(note.getEndCharacterOffset());
            vo.setExcerpt(note.getExcerpt());
            vo.setReflection(note.getReflection());
            vo.setHighlightColor(note.getHighlightColor());
            vo.setPrefixContext(note.getPrefixContext());
            vo.setSuffixContext(note.getSuffixContext());
            vo.setContentVersion(note.getContentVersion());
            vo.setCreateTime(note.getCreateTime());
            vo.setUpdateTime(note.getUpdateTime());
            return vo;
        };
    }

    private ReaderNoteCreateCommand createCommand() {
        ReaderNoteCreateCommand command = new ReaderNoteCreateCommand();
        command.setChapterId(21L);
        command.setStartBlockIndex(1);
        command.setStartCharacterOffset(3);
        command.setEndBlockIndex(2);
        command.setEndCharacterOffset(8);
        command.setExcerpt("剑未出鞘，心已千里。");
        command.setReflection("  ");
        command.setHighlightColor("rose");
        command.setPrefixContext("这是选区前文");
        command.setSuffixContext("这是选区后文");
        command.setContentVersion(1);
        return command;
    }

    private ReaderBookVO book(Long id, int contentVersion) {
        ReaderBookVO book = new ReaderBookVO();
        book.setId(id);
        book.setTitle("测试小说");
        book.setStatus(ReaderBook.STATUS_READY);
        book.setContentVersion(contentVersion);
        return book;
    }

    private ReaderChapterVO chapterVO(Long id, int order, String title) {
        ReaderChapterVO chapter = new ReaderChapterVO();
        chapter.setId(id);
        chapter.setChapterOrder(order);
        chapter.setTitle(title);
        return chapter;
    }

    private ReaderChapter chapter(Long id, Long bookId, int order, String title, String hash) {
        ReaderChapter chapter = new ReaderChapter();
        chapter.setId(id);
        chapter.setBookId(bookId);
        chapter.setChapterOrder(order);
        chapter.setTitle(title);
        chapter.setContentHash(hash);
        return chapter;
    }

    private ReaderNote note(
            Long id,
            Long userId,
            Long bookId,
            Long chapterId,
            int chapterOrder,
            String chapterHash,
            int contentVersion) {
        ReaderNote note = new ReaderNote();
        note.setId(id);
        note.setUserId(userId);
        note.setBookId(bookId);
        note.setChapterId(chapterId);
        note.setChapterOrder(chapterOrder);
        note.setChapterTitle("序章");
        note.setChapterContentHash(chapterHash);
        note.setStartBlockIndex(1);
        note.setStartCharacterOffset(3);
        note.setEndBlockIndex(2);
        note.setEndCharacterOffset(8);
        note.setExcerpt("剑未出鞘，心已千里。");
        note.setHighlightColor("rose");
        note.setContentVersion(contentVersion);
        return note;
    }

    private void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.remove(entityClass);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "reader-note-test");
        assistant.setCurrentNamespace("reader-note-test");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
