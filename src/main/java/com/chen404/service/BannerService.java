package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Banner;

import java.util.List;

public interface BannerService extends IService<Banner> {

    List<Banner> getBannersByPosition(Integer position);
}
