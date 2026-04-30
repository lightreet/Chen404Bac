package com.chen404.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen404.domain.entity.Tag;
import com.chen404.mapper.TagMapper;
import com.chen404.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private static final int STATUS_ENABLED = 1;
    private static final String DEFAULT_TAG_COLOR = "#409EFF";

    @Override
    public List<Tag> getAllTags() {
        return baseMapper.selectAllActive();
    }

    @Override
    public Tag getTagByIdOrSlug(String idOrSlug) {
        if (!StringUtils.hasText(idOrSlug)) {
            return null;
        }
        String trimmed = idOrSlug.trim();
        if (trimmed.matches("^\\d+$")) {
            Tag tag = getById(Long.valueOf(trimmed));
            if (tag != null && Integer.valueOf(1).equals(tag.getStatus()) && !Integer.valueOf(1).equals(tag.getDeleted())) {
                return tag;
            }
            return null;
        }
        return baseMapper.selectBySlug(trimmed);
    }

    @Override
    public Tag findOrCreateByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.trim();
        Tag existing = baseMapper.selectByName(trimmed);
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setName(trimmed);
        tag.setSlug(toSlug(trimmed));
        tag.setColor(DEFAULT_TAG_COLOR);
        tag.setStatus(STATUS_ENABLED);
        tag.setSortOrder(0);
        save(tag);
        return tag;
    }

    private static String toSlug(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-|-$", "");
    }
}
