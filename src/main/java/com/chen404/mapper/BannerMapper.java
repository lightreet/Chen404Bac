package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 轮播图Mapper
 */
@Mapper
public interface BannerMapper extends BaseMapper<Banner> {

    /**
     * 根据位置查询启用的轮播图
     */
    @Select("SELECT * FROM banner WHERE position = #{position} AND status = 1 AND deleted = 0 " +
            "AND (start_time IS NULL OR start_time <= NOW()) " +
            "AND (end_time IS NULL OR end_time >= NOW()) " +
            "ORDER BY sort_order, id")
    List<Banner> selectActiveByPosition(@Param("position") Integer position);
}
