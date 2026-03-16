package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Banner;
import com.chen404.mapper.BannerMapper;
import com.chen404.service.BannerService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    @Override
    public List<Banner> getBannersByPosition(Integer position) {
        return baseMapper.selectActiveByPosition(position);
    }
}
