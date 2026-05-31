package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.domain.entity.MusicPlaylist;
import com.chen404.domain.entity.MusicPlaylistTrack;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.mapper.MusicPlaylistMapper;
import com.chen404.mapper.MusicPlaylistTrackMapper;
import com.chen404.mapper.MusicTrackMapper;
import com.chen404.service.FileReferenceService;
import com.chen404.service.SysFileService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicRadioServiceImplTest {

    @Test
    void shouldListOnlyPublishedTracksForPublicPage() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);

        when(trackMapper.selectList(any())).thenReturn(List.of(buildTrack(1L, "春日来信", "published", "夜读,日系")));

        List<MusicTrackVO> tracks = service.listPublicTracks();

        assertEquals(1, tracks.size());
        assertEquals("春日来信", tracks.get(0).getTitle());
        assertEquals(List.of("夜读", "日系"), tracks.get(0).getTags());

        ArgumentCaptor<LambdaQueryWrapper<MusicTrack>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(trackMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("status"), () -> "公开查询必须过滤 status，实际为: " + sqlSegment);
        assertTrue(sqlSegment.contains("create_time"), () -> "公开查询必须按添加时间排序，实际为: " + sqlSegment);
        assertTrue(sqlSegment.contains("id"), () -> "公开查询必须使用 id 兜底排序，实际为: " + sqlSegment);
    }

    @Test
    void shouldReturnDefaultPublicPlaylistWithPublishedTracksOnly() {
        initTableInfo(MusicPlaylist.class);
        initTableInfo(MusicPlaylistTrack.class);
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);

        MusicPlaylist playlist = new MusicPlaylist();
        playlist.setId(7L);
        playlist.setName("今日电台");
        playlist.setDescription("Lyra 为今天挑的歌");
        playlist.setOpeningText("今晚，Lyra 为你调好一首歌。");
        playlist.setIsDefault(1);
        playlist.setIsPublic(1);
        when(playlistMapper.selectList(any())).thenReturn(List.of(playlist));

        MusicPlaylistTrack first = new MusicPlaylistTrack();
        first.setPlaylistId(7L);
        first.setTrackId(2L);
        first.setSortOrder(0);
        MusicPlaylistTrack second = new MusicPlaylistTrack();
        second.setPlaylistId(7L);
        second.setTrackId(3L);
        second.setSortOrder(1);
        when(playlistTrackMapper.selectList(any())).thenReturn(List.of(first, second));
        when(trackMapper.selectBatchIds(List.of(2L, 3L))).thenReturn(List.of(
                buildTrack(2L, "夜樱", "published", "夜读"),
                buildTrack(3L, "草稿歌", "draft", "草稿")
        ));

        MusicPlaylistVO radio = service.getDefaultRadio();

        assertEquals(7L, radio.getId());
        assertEquals("今日电台", radio.getName());
        assertEquals(1, radio.getTracks().size());
        assertEquals("夜樱", radio.getTracks().get(0).getTitle());
        assertFalse(radio.getTracks().stream().anyMatch(track -> "草稿歌".equals(track.getTitle())));
    }

    @Test
    void shouldReturnAdminPlaylistsWithDraftTracksForEditing() {
        initTableInfo(MusicPlaylist.class);
        initTableInfo(MusicPlaylistTrack.class);
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);

        MusicPlaylist playlist = new MusicPlaylist();
        playlist.setId(9L);
        playlist.setName("编辑中的歌单");
        playlist.setIsDefault(0);
        playlist.setIsPublic(0);
        when(playlistMapper.selectList(any())).thenReturn(List.of(playlist));

        MusicPlaylistTrack link = new MusicPlaylistTrack();
        link.setPlaylistId(9L);
        link.setTrackId(4L);
        link.setSortOrder(0);
        when(playlistTrackMapper.selectList(any())).thenReturn(List.of(link));
        when(trackMapper.selectBatchIds(List.of(4L))).thenReturn(List.of(
                buildTrack(4L, "草稿歌", MusicTrack.STATUS_DRAFT, "draft")
        ));

        List<MusicPlaylistVO> playlists = service.listAdminPlaylists();

        assertEquals(1, playlists.size());
        assertEquals(1, playlists.get(0).getTracks().size());
        assertEquals("草稿歌", playlists.get(0).getTracks().get(0).getTitle());
    }

    @Test
    void shouldConvertUploadedAudioAndCoverToPermanentWhenCreatingTrack() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        MusicTrackUpsertCommand command = buildTrackCommand(
                "https://cdn.example.com/audio/temp.mp3",
                "https://cdn.example.com/cover/temp.webp");

        when(trackMapper.insert(any(MusicTrack.class))).thenAnswer(invocation -> {
            MusicTrack inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        });
        when(trackMapper.selectById(42L)).thenReturn(buildTrack(42L, "Night Walk", MusicTrack.STATUS_DRAFT, "radio"));

        service.createTrack(command);

        verify(sysFileService).convertToPermanent(
                eq(List.of("https://cdn.example.com/audio/temp.mp3")),
                eq(SysFile.RefType.MUSIC_AUDIO),
                eq(42L));
        verify(sysFileService).convertToPermanent(
                eq(List.of("https://cdn.example.com/cover/temp.webp")),
                eq(SysFile.RefType.MUSIC_COVER),
                eq(42L));
    }

    @Test
    void shouldReturnDraftTrackForAdminEditor() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        MusicTrack draftTrack = buildTrack(11L, "草稿里的歌", MusicTrack.STATUS_DRAFT, "draft,editor");
        when(trackMapper.selectById(11L)).thenReturn(draftTrack);

        MusicTrackVO track = service.getAdminTrack(11L);

        assertEquals(11L, track.getId());
        assertEquals("草稿里的歌", track.getTitle());
        assertEquals(MusicTrack.STATUS_DRAFT, track.getStatus());
        assertEquals(List.of("draft", "editor"), track.getTags());
        verify(trackMapper).selectById(11L);
    }

    @Test
    void shouldConvertUploadedAudioAndCoverToPermanentWhenUpdatingTrack() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        MusicTrack existing = buildTrack(7L, "Old Song", MusicTrack.STATUS_DRAFT, "radio");
        MusicTrackUpsertCommand command = buildTrackCommand(
                "https://cdn.example.com/audio/new-temp.mp3",
                "https://cdn.example.com/cover/new-temp.webp");

        when(trackMapper.selectById(7L)).thenReturn(existing);

        service.updateTrack(7L, command);

        verify(sysFileService).convertToPermanent(
                eq(List.of("https://cdn.example.com/audio/new-temp.mp3")),
                eq(SysFile.RefType.MUSIC_AUDIO),
                eq(7L));
        verify(sysFileService).convertToPermanent(
                eq(List.of("https://cdn.example.com/cover/new-temp.webp")),
                eq(SysFile.RefType.MUSIC_COVER),
                eq(7L));
    }

    @Test
    void shouldRejectInvalidLrcWhenCreatingTrack() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        MusicTrackUpsertCommand command = buildTrackCommand(
                "https://cdn.example.com/audio/temp.mp3",
                "https://cdn.example.com/cover/temp.webp");
        command.setLyricType(MusicTrack.LYRIC_TYPE_LRC);
        command.setLyrics("[00:12.00]第一句歌词\n这行缺少时间轴");

        assertThrows(BadRequestException.class, () -> service.createTrack(command));

        verify(trackMapper, never()).insert(any(MusicTrack.class));
    }

    @Test
    void shouldAcceptLrcMetadataWhenCreatingTrack() {
        initTableInfo(MusicTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        MusicTrackUpsertCommand command = buildTrackCommand(
                "https://cdn.example.com/audio/temp.mp3",
                "https://cdn.example.com/cover/temp.webp");
        command.setLyricType(MusicTrack.LYRIC_TYPE_LRC);
        command.setLyrics("[ti:Night Walk]\n[ar:helychen]\n[00:12.00]第一句歌词");

        when(trackMapper.insert(any(MusicTrack.class))).thenAnswer(invocation -> {
            MusicTrack inserted = invocation.getArgument(0);
            inserted.setId(43L);
            return 1;
        });
        when(trackMapper.selectById(43L)).thenReturn(buildTrack(43L, "Night Walk", MusicTrack.STATUS_DRAFT, "radio"));

        service.createTrack(command);

        verify(trackMapper).insert(any(MusicTrack.class));
    }

    @Test
    void shouldDeleteUnreferencedTrack() {
        initTableInfo(MusicTrack.class);
        initTableInfo(MusicPlaylistTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        when(trackMapper.selectById(5L)).thenReturn(buildTrack(5L, "Unlinked Song", MusicTrack.STATUS_DRAFT, "radio"));
        when(playlistTrackMapper.selectCount(any())).thenReturn(0L);

        service.deleteTrack(5L);

        verify(trackMapper).deleteById(5L);
    }

    @Test
    void shouldRejectDeletingTrackReferencedByPlaylist() {
        initTableInfo(MusicTrack.class);
        initTableInfo(MusicPlaylistTrack.class);
        MusicTrackMapper trackMapper = mock(MusicTrackMapper.class);
        MusicPlaylistMapper playlistMapper = mock(MusicPlaylistMapper.class);
        MusicPlaylistTrackMapper playlistTrackMapper = mock(MusicPlaylistTrackMapper.class);
        SysFileService sysFileService = mock(SysFileService.class);
        MusicRadioServiceImpl service = buildService(trackMapper, playlistMapper, playlistTrackMapper, sysFileService);
        when(trackMapper.selectById(5L)).thenReturn(buildTrack(5L, "Linked Song", MusicTrack.STATUS_DRAFT, "radio"));
        when(playlistTrackMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> service.deleteTrack(5L));

        verify(trackMapper, never()).deleteById(5L);
    }

    private MusicTrack buildTrack(Long id, String title, String status, String tags) {
        MusicTrack track = new MusicTrack();
        track.setId(id);
        track.setTitle(title);
        track.setArtist("helychen");
        track.setAlbum("Chen404");
        track.setStatus(status);
        track.setTags(tags);
        track.setSortOrder(0);
        return track;
    }

    private MusicTrackUpsertCommand buildTrackCommand(String audioUrl, String coverUrl) {
        MusicTrackUpsertCommand command = new MusicTrackUpsertCommand();
        command.setTitle("Night Walk");
        command.setArtist("helychen");
        command.setAudioUrl(audioUrl);
        command.setCoverUrl(coverUrl);
        command.setStatus(MusicTrack.STATUS_DRAFT);
        return command;
    }

    private MusicRadioServiceImpl buildService(
            MusicTrackMapper trackMapper,
            MusicPlaylistMapper playlistMapper,
            MusicPlaylistTrackMapper playlistTrackMapper,
            SysFileService sysFileService) {
        return new MusicRadioServiceImpl(
                trackMapper,
                playlistMapper,
                playlistTrackMapper,
                sysFileService,
                mock(FileReferenceService.class)
        );
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
