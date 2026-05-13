package com.chen404.service.impl;

import com.chen404.service.TravelMemoryImageMetadataService;
import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 旅行纪念图片元数据解析实现，优先从原始图片 EXIF 中提取坐标和拍摄时间。
 */
@Slf4j
@Service
public class TravelMemoryImageMetadataServiceImpl implements TravelMemoryImageMetadataService {

    private static final int COORDINATE_SCALE = 6;

    @Override
    public TravelMemoryImageMetadata extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new TravelMemoryImageMetadata(null, null, null);
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.getInputStream());
            return new TravelMemoryImageMetadata(
                    extractLatitude(metadata),
                    extractLongitude(metadata),
                    extractShotAt(metadata)
            );
        } catch (Exception ex) {
            log.warn("[TRAVEL_MEMORY_EXIF_PARSE_FAIL] fileName={} message={}",
                    file.getOriginalFilename(), ex.getMessage());
            return new TravelMemoryImageMetadata(null, null, null);
        }
    }

    private BigDecimal extractLatitude(Metadata metadata) {
        GeoLocation geoLocation = extractGeoLocation(metadata);
        if (geoLocation == null) {
            return null;
        }
        return scaleCoordinate(geoLocation.getLatitude());
    }

    private BigDecimal extractLongitude(Metadata metadata) {
        GeoLocation geoLocation = extractGeoLocation(metadata);
        if (geoLocation == null) {
            return null;
        }
        return scaleCoordinate(geoLocation.getLongitude());
    }

    private GeoLocation extractGeoLocation(Metadata metadata) {
        GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        if (gpsDirectory == null) {
            return null;
        }
        GeoLocation geoLocation = gpsDirectory.getGeoLocation();
        if (geoLocation == null || geoLocation.isZero()) {
            return null;
        }
        return geoLocation;
    }

    private LocalDateTime extractShotAt(Metadata metadata) {
        ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exifDirectory == null) {
            return null;
        }
        Date original = exifDirectory.getDateOriginal();
        if (original != null) {
            return toLocalDateTime(original);
        }
        Date digitized = exifDirectory.getDateDigitized();
        if (digitized != null) {
            return toLocalDateTime(digitized);
        }
        return null;
    }

    private BigDecimal scaleCoordinate(double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }
}
