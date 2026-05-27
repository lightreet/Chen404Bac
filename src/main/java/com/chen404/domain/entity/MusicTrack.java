package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("music_track")
public class MusicTrack implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String LYRIC_TYPE_PLAIN = "plain";
    public static final String LYRIC_TYPE_LRC = "lrc";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String artist;

    private String album;

    private Integer releaseYear;

    private String language;

    private String genre;

    private String tags;

    private Long audioFileId;

    private String audioUrl;

    private Long coverFileId;

    private String coverUrl;

    private String lyricType;

    private String lyrics;

    private String lyricSource;

    private String recommendation;

    private String moodText;

    private String status;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
