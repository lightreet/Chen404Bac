package com.chen404.converter;

import com.chen404.domain.dto.UploadFileVO;
import com.chen404.domain.entity.SysFile;
import com.chen404.service.TravelMemoryImageMetadataService.TravelMemoryImageMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 手机上传文件与展示模型转换，票据 URL 由服务在副本上单独签发。 */
@Mapper(componentModel = "spring")
public interface TravelMobileUploadConverter {
    /** 合并已存储文件信息和 EXIF，URL 留给服务按访问范围生成。 */
    @Mapping(target = "id", source = "file.id")
    @Mapping(target = "name", source = "file.fileName")
    @Mapping(target = "size", source = "file.fileSize")
    @Mapping(target = "url", ignore = true)
    @Mapping(target = "latitude", source = "metadata.latitude")
    @Mapping(target = "longitude", source = "metadata.longitude")
    @Mapping(target = "shotAt", source = "metadata.shotAt")
    UploadFileVO fromFile(SysFile file, TravelMemoryImageMetadata metadata);

    /** 复制展示字段，避免签发票据时修改会话内保存的稳定地址。 */
    UploadFileVO copy(UploadFileVO source);
}
