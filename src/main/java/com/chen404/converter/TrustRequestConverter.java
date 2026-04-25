package com.chen404.converter;

import com.chen404.domain.dto.TrustRequestAttachmentVO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.domain.entity.UserTrustRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.StringUtils;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrustRequestConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "request.id")
    @Mapping(target = "userId", source = "request.userId")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "reason", source = "request.reason")
    @Mapping(target = "contactEmail", source = "request.contactEmail")
    @Mapping(target = "reviewNote", source = "request.reviewNote")
    @Mapping(target = "reviewedBy", source = "request.reviewedBy")
    @Mapping(target = "reviewedAt", source = "request.reviewedAt")
    @Mapping(target = "createTime", source = "request.createTime")
    @Mapping(target = "updateTime", source = "request.updateTime")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "nickname", source = "user.nickname")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "userTrustLevel", source = "user.trustLevel")
    @Mapping(target = "reviewerName", expression = "java(resolveReviewerName(reviewer))")
    TrustRequestVO toVO(UserTrustRequest request, User user, User reviewer);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "fileName", source = "fileOriginalName")
    @Mapping(target = "fileUrl", source = "fileUrl")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "createTime", source = "createTime")
    TrustRequestAttachmentVO toAttachmentVO(SysFile file);

    List<TrustRequestAttachmentVO> toAttachmentVOList(List<SysFile> files);

    default String resolveReviewerName(User reviewer) {
        if (reviewer == null) {
            return null;
        }
        return StringUtils.hasText(reviewer.getNickname()) ? reviewer.getNickname() : reviewer.getUsername();
    }
}
