package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chen404.domain.dto.MusicPlaylistTracksCommand;
import com.chen404.domain.dto.MusicPlaylistUpsertCommand;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.domain.entity.MusicPlaylist;
import com.chen404.domain.entity.MusicPlaylistTrack;
import com.chen404.domain.entity.MusicTrack;
import com.chen404.domain.entity.SysFile;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.MusicPlaylistMapper;
import com.chen404.mapper.MusicPlaylistTrackMapper;
import com.chen404.mapper.MusicTrackMapper;
import com.chen404.service.FileReferenceService;
import com.chen404.service.MusicRadioService;
import com.chen404.service.SysFileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 音乐馆歌曲、分类和分类曲目编排的业务服务实现。
 */
@Service
public class MusicRadioServiceImpl implements MusicRadioService {

    private static final int YES = 1;
    private static final int NO = 0;
    private static final int DEFAULT_SORT_ORDER = 0;
    private static final Pattern LRC_TIME_LINE_PATTERN = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?].*$");
    private static final Pattern LRC_METADATA_LINE_PATTERN = Pattern.compile("^\\[[a-zA-Z]+:.*]$");

    private final MusicTrackMapper musicTrackMapper;
    private final MusicPlaylistMapper musicPlaylistMapper;
    private final MusicPlaylistTrackMapper musicPlaylistTrackMapper;
    private final SysFileService sysFileService;
    private final FileReferenceService fileReferenceService;

    public MusicRadioServiceImpl(
            MusicTrackMapper musicTrackMapper,
            MusicPlaylistMapper musicPlaylistMapper,
            MusicPlaylistTrackMapper musicPlaylistTrackMapper,
            SysFileService sysFileService,
            FileReferenceService fileReferenceService) {
        this.musicTrackMapper = musicTrackMapper;
        this.musicPlaylistMapper = musicPlaylistMapper;
        this.musicPlaylistTrackMapper = musicPlaylistTrackMapper;
        this.sysFileService = sysFileService;
        this.fileReferenceService = fileReferenceService;
    }

    @Override
    public List<MusicTrackVO> listPublicTracks() {
        LambdaQueryWrapper<MusicTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicTrack::getStatus, MusicTrack.STATUS_PUBLISHED)
                .orderByDesc(MusicTrack::getCreateTime)
                .orderByDesc(MusicTrack::getId);
        return musicTrackMapper.selectList(wrapper).stream().map(this::toTrackVO).toList();
    }

    @Override
    public MusicTrackVO getPublicTrack(Long id) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null || !MusicTrack.STATUS_PUBLISHED.equals(track.getStatus())) {
            throw new ResourceNotFoundException("音乐不存在");
        }
        return toTrackVO(track);
    }

    @Override
    public List<MusicPlaylistVO> listPublicPlaylists() {
        LambdaQueryWrapper<MusicPlaylist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPlaylist::getIsPublic, YES)
                .orderByDesc(MusicPlaylist::getCreateTime)
                .orderByDesc(MusicPlaylist::getId);
        return musicPlaylistMapper.selectList(wrapper).stream()
                .map(playlist -> toPlaylistVO(playlist, false))
                .toList();
    }

    @Override
    public MusicPlaylistVO getPublicPlaylist(Long id) {
        MusicPlaylist playlist = musicPlaylistMapper.selectById(id);
        if (playlist == null || !Integer.valueOf(YES).equals(playlist.getIsPublic())) {
            throw new ResourceNotFoundException("分类不存在");
        }
        return toPlaylistVO(playlist, true);
    }

    @Override
    public MusicPlaylistVO getDefaultRadio() {
        LambdaQueryWrapper<MusicPlaylist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPlaylist::getIsDefault, YES)
                .eq(MusicPlaylist::getIsPublic, YES)
                .orderByDesc(MusicPlaylist::getCreateTime)
                .orderByDesc(MusicPlaylist::getId)
                .last("LIMIT 1");
        List<MusicPlaylist> playlists = musicPlaylistMapper.selectList(wrapper);
        if (playlists.isEmpty()) {
            MusicPlaylistVO empty = new MusicPlaylistVO();
            empty.setName("音乐馆");
            empty.setDescription("默认播放集还没有准备好");
            empty.setDefaultPlaylist(true);
            empty.setPublicPlaylist(true);
            empty.setTracks(List.of());
            return empty;
        }
        return toPlaylistVO(playlists.get(0), true);
    }

    @Override
    public List<MusicTrackVO> listAdminTracks() {
        LambdaQueryWrapper<MusicTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MusicTrack::getCreateTime).orderByDesc(MusicTrack::getId);
        return musicTrackMapper.selectList(wrapper).stream().map(this::toTrackVO).toList();
    }

    @Override
    public MusicTrackVO getAdminTrack(Long id) {
        return toTrackVO(requireTrack(id));
    }

    @Override
    @Transactional
    public MusicTrackVO createTrack(MusicTrackUpsertCommand command) {
        MusicTrack track = toTrack(command);
        musicTrackMapper.insert(track);
        convertTrackFilesToPermanent(track.getId(), track);
        syncTrackFileReferences(track.getId(), track);
        return toTrackVO(musicTrackMapper.selectById(track.getId()));
    }

    @Override
    @Transactional
    public MusicTrackVO updateTrack(Long id, MusicTrackUpsertCommand command) {
        requireTrack(id);
        MusicTrack track = toTrack(command);
        track.setId(id);
        musicTrackMapper.updateById(track);
        convertTrackFilesToPermanent(id, track);
        syncTrackFileReferences(id, track);
        return toTrackVO(musicTrackMapper.selectById(id));
    }

    @Override
    @Transactional
    public MusicTrackVO updateTrackStatus(Long id, String status) {
        MusicTrack track = requireTrack(id);
        track.setStatus(normalizeStatus(status));
        musicTrackMapper.updateById(track);
        return toTrackVO(musicTrackMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteTrack(Long id) {
        requireTrack(id);
        // 删除歌曲时先解除所有分类归属，避免分类关系阻塞歌曲本体删除。
        LambdaQueryWrapper<MusicPlaylistTrack> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MusicPlaylistTrack::getTrackId, id);
        musicPlaylistTrackMapper.delete(deleteWrapper);

        musicTrackMapper.deleteById(id);
        fileReferenceService.syncMusicTrackReferences(id, null, null, null, null);
    }

    @Override
    public List<MusicPlaylistVO> listAdminPlaylists() {
        LambdaQueryWrapper<MusicPlaylist> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MusicPlaylist::getCreateTime).orderByDesc(MusicPlaylist::getId);
        return musicPlaylistMapper.selectList(wrapper).stream()
                .map(playlist -> toPlaylistVO(playlist, true, false))
                .toList();
    }

    @Override
    @Transactional
    public MusicPlaylistVO createPlaylist(MusicPlaylistUpsertCommand command) {
        MusicPlaylist playlist = toPlaylist(command);
        if (Integer.valueOf(YES).equals(playlist.getIsDefault())) {
            clearDefaultPlaylist();
        }
        musicPlaylistMapper.insert(playlist);
        return toPlaylistVO(musicPlaylistMapper.selectById(playlist.getId()), true, false);
    }

    @Override
    @Transactional
    public MusicPlaylistVO updatePlaylist(Long id, MusicPlaylistUpsertCommand command) {
        requirePlaylist(id);
        MusicPlaylist playlist = toPlaylist(command);
        playlist.setId(id);
        if (Integer.valueOf(YES).equals(playlist.getIsDefault())) {
            clearDefaultPlaylist();
        }
        musicPlaylistMapper.updateById(playlist);
        return toPlaylistVO(musicPlaylistMapper.selectById(id), true, false);
    }

    @Override
    @Transactional
    public MusicPlaylistVO savePlaylistTracks(Long id, MusicPlaylistTracksCommand command) {
        MusicPlaylist playlist = requirePlaylist(id);
        LambdaQueryWrapper<MusicPlaylistTrack> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MusicPlaylistTrack::getPlaylistId, id);
        musicPlaylistTrackMapper.delete(deleteWrapper);

        List<Long> trackIds = command == null ? List.of() : command.getTrackIds();
        for (int index = 0; index < trackIds.size(); index++) {
            Long trackId = trackIds.get(index);
            requireTrack(trackId);
            MusicPlaylistTrack row = new MusicPlaylistTrack();
            row.setPlaylistId(id);
            row.setTrackId(trackId);
            row.setSortOrder(index);
            musicPlaylistTrackMapper.insert(row);
        }
        return toPlaylistVO(playlist, true, false);
    }

    @Override
    @Transactional
    public void deletePlaylist(Long id) {
        requirePlaylist(id);

        // 删除分类时仅移除分类本体与关联关系，不影响歌曲资源本身。
        LambdaQueryWrapper<MusicPlaylistTrack> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MusicPlaylistTrack::getPlaylistId, id);
        musicPlaylistTrackMapper.delete(deleteWrapper);

        musicPlaylistMapper.deleteById(id);
    }

    @Override
    @Transactional
    public MusicPlaylistVO setDefaultPlaylist(Long id) {
        MusicPlaylist playlist = requirePlaylist(id);
        clearDefaultPlaylist();
        playlist.setIsDefault(YES);
        playlist.setIsPublic(YES);
        musicPlaylistMapper.updateById(playlist);
        return toPlaylistVO(musicPlaylistMapper.selectById(id), true, false);
    }

    private MusicTrack requireTrack(Long id) {
        MusicTrack track = musicTrackMapper.selectById(id);
        if (track == null) {
            throw new ResourceNotFoundException("音乐不存在");
        }
        return track;
    }

    private MusicPlaylist requirePlaylist(Long id) {
        MusicPlaylist playlist = musicPlaylistMapper.selectById(id);
        if (playlist == null) {
            throw new ResourceNotFoundException("分类不存在");
        }
        return playlist;
    }

    private MusicPlaylistVO toPlaylistVO(MusicPlaylist playlist, boolean includeTracks) {
        return toPlaylistVO(playlist, includeTracks, true);
    }

    private MusicPlaylistVO toPlaylistVO(MusicPlaylist playlist, boolean includeTracks, boolean publishedOnly) {
        MusicPlaylistVO vo = new MusicPlaylistVO();
        vo.setId(playlist.getId());
        vo.setName(playlist.getName());
        vo.setDescription(playlist.getDescription());
        vo.setCoverFileId(playlist.getCoverFileId());
        vo.setCoverUrl(playlist.getCoverUrl());
        vo.setOpeningText(playlist.getOpeningText());
        vo.setDefaultPlaylist(Integer.valueOf(YES).equals(playlist.getIsDefault()));
        vo.setPublicPlaylist(Integer.valueOf(YES).equals(playlist.getIsPublic()));
        vo.setSortOrder(playlist.getSortOrder());
        vo.setCreateTime(playlist.getCreateTime());
        vo.setUpdateTime(playlist.getUpdateTime());
        vo.setTracks(includeTracks ? listTracksByPlaylistId(playlist.getId(), publishedOnly) : List.of());
        return vo;
    }

    private List<MusicTrackVO> listTracksByPlaylistId(Long playlistId, boolean publishedOnly) {
        LambdaQueryWrapper<MusicPlaylistTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicPlaylistTrack::getPlaylistId, playlistId)
                .orderByAsc(MusicPlaylistTrack::getSortOrder)
                .orderByAsc(MusicPlaylistTrack::getId);
        List<MusicPlaylistTrack> links = musicPlaylistTrackMapper.selectList(wrapper);
        if (links.isEmpty()) {
            return List.of();
        }

        List<Long> ids = links.stream().map(MusicPlaylistTrack::getTrackId).filter(Objects::nonNull).toList();
        Map<Long, MusicTrack> trackMap = musicTrackMapper.selectBatchIds(ids).stream()
                .filter(track -> !publishedOnly || MusicTrack.STATUS_PUBLISHED.equals(track.getStatus()))
                .collect(Collectors.toMap(MusicTrack::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<MusicTrackVO> result = new ArrayList<>();
        for (MusicPlaylistTrack link : links) {
            MusicTrack track = trackMap.get(link.getTrackId());
            if (track != null) {
                result.add(toTrackVO(track));
            }
        }
        return result;
    }

    private MusicTrackVO toTrackVO(MusicTrack track) {
        MusicTrackVO vo = new MusicTrackVO();
        vo.setId(track.getId());
        vo.setTitle(track.getTitle());
        vo.setArtist(track.getArtist());
        vo.setAlbum(track.getAlbum());
        vo.setReleaseYear(track.getReleaseYear());
        vo.setLanguage(track.getLanguage());
        vo.setGenre(track.getGenre());
        vo.setTags(parseTags(track.getTags()));
        vo.setAudioFileId(track.getAudioFileId());
        vo.setAudioUrl(track.getAudioUrl());
        vo.setCoverFileId(track.getCoverFileId());
        vo.setCoverUrl(track.getCoverUrl());
        vo.setLyricType(track.getLyricType());
        vo.setLyrics(track.getLyrics());
        vo.setLyricSource(track.getLyricSource());
        vo.setRecommendation(track.getRecommendation());
        vo.setMoodText(track.getMoodText());
        vo.setStatus(track.getStatus());
        vo.setSortOrder(track.getSortOrder());
        vo.setCreateTime(track.getCreateTime());
        vo.setUpdateTime(track.getUpdateTime());
        return vo;
    }

    private MusicTrack toTrack(MusicTrackUpsertCommand command) {
        MusicTrack track = new MusicTrack();
        String lyricType = normalizeLyricType(command.getLyricType());
        validateLyrics(lyricType, command.getLyrics());
        track.setTitle(trim(command.getTitle()));
        track.setArtist(trim(command.getArtist()));
        track.setAlbum(trim(command.getAlbum()));
        track.setReleaseYear(command.getReleaseYear());
        track.setLanguage(trim(command.getLanguage()));
        track.setGenre(trim(command.getGenre()));
        track.setTags(joinTags(command.getTags()));
        track.setAudioFileId(command.getAudioFileId());
        track.setAudioUrl(trim(command.getAudioUrl()));
        track.setCoverFileId(command.getCoverFileId());
        track.setCoverUrl(trim(command.getCoverUrl()));
        track.setLyricType(lyricType);
        track.setLyrics(command.getLyrics());
        track.setLyricSource(trim(command.getLyricSource()));
        track.setRecommendation(trim(command.getRecommendation()));
        track.setMoodText(trim(command.getMoodText()));
        track.setStatus(normalizeStatus(command.getStatus()));
        track.setSortOrder(DEFAULT_SORT_ORDER);
        return track;
    }

    private void convertTrackFilesToPermanent(Long trackId, MusicTrack track) {
        if (trackId == null || track == null) {
            return;
        }
        if (StringUtils.hasText(track.getAudioUrl())) {
            sysFileService.convertToPermanent(List.of(track.getAudioUrl()), SysFile.RefType.MUSIC_AUDIO, trackId);
        }
        if (StringUtils.hasText(track.getCoverUrl())) {
            sysFileService.convertToPermanent(List.of(track.getCoverUrl()), SysFile.RefType.MUSIC_COVER, trackId);
        }
    }

    private void syncTrackFileReferences(Long trackId, MusicTrack track) {
        if (trackId == null || track == null) {
            return;
        }
        fileReferenceService.syncMusicTrackReferences(
                trackId,
                track.getAudioFileId(),
                track.getAudioUrl(),
                track.getCoverFileId(),
                track.getCoverUrl()
        );
    }

    private MusicPlaylist toPlaylist(MusicPlaylistUpsertCommand command) {
        MusicPlaylist playlist = new MusicPlaylist();
        playlist.setName(trim(command.getName()));
        playlist.setDescription(trim(command.getDescription()));
        playlist.setCoverFileId(command.getCoverFileId());
        playlist.setCoverUrl(trim(command.getCoverUrl()));
        playlist.setOpeningText(trim(command.getOpeningText()));
        playlist.setIsDefault(Boolean.TRUE.equals(command.getDefaultPlaylist()) ? YES : NO);
        playlist.setIsPublic(command.getPublicPlaylist() == null || Boolean.TRUE.equals(command.getPublicPlaylist()) ? YES : NO);
        playlist.setSortOrder(DEFAULT_SORT_ORDER);
        return playlist;
    }

    private void clearDefaultPlaylist() {
        LambdaUpdateWrapper<MusicPlaylist> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MusicPlaylist::getIsDefault, YES).set(MusicPlaylist::getIsDefault, NO);
        musicPlaylistMapper.update(null, wrapper);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return MusicTrack.STATUS_DRAFT;
        }
        String normalized = status.trim().toLowerCase();
        if (List.of(MusicTrack.STATUS_DRAFT, MusicTrack.STATUS_PUBLISHED, MusicTrack.STATUS_ARCHIVED).contains(normalized)) {
            return normalized;
        }
        throw new BadRequestException("不支持的音乐状态");
    }

    private String normalizeLyricType(String lyricType) {
        if (!StringUtils.hasText(lyricType)) {
            return MusicTrack.LYRIC_TYPE_PLAIN;
        }
        String normalized = lyricType.trim().toLowerCase();
        if (List.of(MusicTrack.LYRIC_TYPE_PLAIN, MusicTrack.LYRIC_TYPE_LRC).contains(normalized)) {
            return normalized;
        }
        throw new BadRequestException("不支持的歌词类型");
    }

    private void validateLyrics(String lyricType, String lyrics) {
        if (!MusicTrack.LYRIC_TYPE_LRC.equals(lyricType) || !StringUtils.hasText(lyrics)) {
            return;
        }
        String[] lines = lyrics.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (!isSupportedLrcLine(line)) {
                throw new BadRequestException("LRC 歌词第 " + (index + 1) + " 行格式不正确");
            }
        }
    }

    private boolean isSupportedLrcLine(String line) {
        return LRC_TIME_LINE_PATTERN.matcher(line).matches()
                || LRC_METADATA_LINE_PATTERN.matcher(line).matches();
    }

    private List<String> parseTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return List.of(tags.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
