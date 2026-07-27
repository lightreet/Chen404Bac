package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅行纪念地点实体。
 */
@Data
@TableName("travel_memory_location")
public class TravelMemoryLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String province;

    private String city;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String summaryNote;

    private String coverImage;

    private LocalDateTime visitedAt;

    private LocalDateTime visitedEndAt;

    private Integer status;

    private Integer visibility;

    private Integer sortOrder;

    private Long createdBy;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private Integer entryCount;

    @TableField(exist = false)
    private List<TravelMemoryEntry> entries;

    @TableField(exist = false)
    private List<TravelMemoryStop> stops;

    @TableField(exist = false)
    private User creator;

    @TableField(exist = false)
    private Boolean canEdit;

    @TableField(exist = false)
    private Boolean canDelete;
}
