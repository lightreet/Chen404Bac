package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_trust_request")
public class UserTrustRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer status;

    private String reason;

    private String contactEmail;

    private String reviewNote;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    @JsonIgnore
    private String approveTokenHash;

    @JsonIgnore
    private LocalDateTime approveTokenExpireAt;

    @JsonIgnore
    private LocalDateTime approveTokenUsedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public interface Status {
        int PENDING = 0;
        int APPROVED = 1;
        int REJECTED = 2;
    }
}
