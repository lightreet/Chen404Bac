package com.chen404.service;

import com.chen404.domain.entity.Banner;

import java.util.List;

public interface BannerService {

    List<Banner> getBannersByPosition(Integer position);
}
