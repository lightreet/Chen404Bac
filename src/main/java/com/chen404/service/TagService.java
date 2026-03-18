package com.chen404.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen404.domain.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    List<Tag> getAllTags();

    /**
     * 按名称查找标签，不存在则创建后返回（用于文章编辑时用户输入新标签名）
     */
    Tag findOrCreateByName(String name);
}
