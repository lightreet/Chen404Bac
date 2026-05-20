package com.chen404.service;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 旅行纪念图片元数据解析服务，用于读取 EXIF 中的坐标与拍摄时间。
 */
public interface TravelMemoryImageMetadataService {

    /**
     * 从上传图片中提取可用的经纬度和拍摄时间。
     */
    TravelMemoryImageMetadata extract(MultipartFile file);

    /**
     * 旅行纪念图片元数据结果。
     */
    record TravelMemoryImageMetadata(
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime shotAt
    ) {
    }
}
