package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;
import com.chen404.mapper.EmojiItemMapper;
import com.chen404.mapper.EmojiPackMapper;
import com.chen404.service.EmojiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class EmojiServiceImpl implements EmojiService {

    @Autowired
    private EmojiPackMapper emojiPackMapper;

    @Autowired
    private EmojiItemMapper emojiItemMapper;

    @Override
    public Page<EmojiPack> pageAllPacks(int page, int size) {
        Page<EmojiPack> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<EmojiPack> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(EmojiPack::getSort).orderByAsc(EmojiPack::getId);
        return emojiPackMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<EmojiPack> listAllPacks() {
        LambdaQueryWrapper<EmojiPack> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(EmojiPack::getSort).orderByAsc(EmojiPack::getId);
        return emojiPackMapper.selectList(wrapper);
    }

    @Override
    public List<EmojiPack> listEnabledPacks() {
        LambdaQueryWrapper<EmojiPack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPack::getEnabled, 1).orderByAsc(EmojiPack::getSort).orderByAsc(EmojiPack::getId);
        return emojiPackMapper.selectList(wrapper);
    }

    @Override
    public Page<EmojiItem> pageAllItems(int page, int size, String packCode) {
        Page<EmojiItem> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<EmojiItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(packCode)) {
            wrapper.eq(EmojiItem::getPackCode, packCode);
        }
        wrapper.orderByAsc(EmojiItem::getSort).orderByAsc(EmojiItem::getId);
        return emojiItemMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<EmojiItem> listAllItems(String packCode) {
        LambdaQueryWrapper<EmojiItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(packCode)) {
            wrapper.eq(EmojiItem::getPackCode, packCode);
        }
        wrapper.orderByAsc(EmojiItem::getSort).orderByAsc(EmojiItem::getId);
        return emojiItemMapper.selectList(wrapper);
    }

    @Override
    public List<EmojiItem> listEnabledItems(String scene, String packCode) {
        LambdaQueryWrapper<EmojiItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiItem::getEnabled, 1);
        if (StringUtils.hasText(packCode)) {
            wrapper.eq(EmojiItem::getPackCode, packCode);
        }
        // scene 暂时不做后端策略过滤；策略在前端控制。后续可扩展 scene_policy 表。
        wrapper.orderByAsc(EmojiItem::getSort).orderByAsc(EmojiItem::getId);
        return emojiItemMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmojiPack upsertPack(EmojiPackUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getPackCode())) {
            throw new IllegalArgumentException("packCode 不能为空");
        }
        EmojiPack existing = emojiPackMapper.selectOne(new LambdaQueryWrapper<EmojiPack>()
                .eq(EmojiPack::getPackCode, dto.getPackCode()));
        if (existing == null) {
            EmojiPack pack = new EmojiPack();
            pack.setPackCode(dto.getPackCode());
            pack.setName(dto.getName());
            pack.setDescription(dto.getDescription());
            pack.setIconUrl(dto.getIconUrl());
            pack.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
            pack.setSort(dto.getSort() == null ? 0 : dto.getSort());
            emojiPackMapper.insert(pack);
            return pack;
        }
        existing.setName(dto.getName() != null ? dto.getName() : existing.getName());
        existing.setDescription(dto.getDescription());
        existing.setIconUrl(dto.getIconUrl());
        if (dto.getEnabled() != null) existing.setEnabled(dto.getEnabled());
        if (dto.getSort() != null) existing.setSort(dto.getSort());
        emojiPackMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmojiItem upsertItem(EmojiItemUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getShortcode()) || !StringUtils.hasText(dto.getPackCode())) {
            throw new IllegalArgumentException("packCode/shortcode 不能为空");
        }
        EmojiItem existing = emojiItemMapper.selectOne(new LambdaQueryWrapper<EmojiItem>()
                .eq(EmojiItem::getShortcode, dto.getShortcode()));
        if (existing == null) {
            EmojiItem item = new EmojiItem();
            item.setPackCode(dto.getPackCode());
            item.setShortcode(dto.getShortcode());
            item.setLabel(dto.getLabel());
            item.setCategory(dto.getCategory());
            item.setType(dto.getType());
            item.setUnicode(dto.getUnicode());
            item.setAssetUrl(dto.getAssetUrl());
            item.setWidth(dto.getWidth());
            item.setHeight(dto.getHeight());
            item.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
            item.setSort(dto.getSort() == null ? 0 : dto.getSort());
            emojiItemMapper.insert(item);
            return item;
        }
        existing.setPackCode(dto.getPackCode());
        existing.setLabel(dto.getLabel() != null ? dto.getLabel() : existing.getLabel());
        existing.setCategory(dto.getCategory() != null ? dto.getCategory() : existing.getCategory());
        if (dto.getType() != null) existing.setType(dto.getType());
        existing.setUnicode(dto.getUnicode());
        existing.setAssetUrl(dto.getAssetUrl());
        existing.setWidth(dto.getWidth());
        existing.setHeight(dto.getHeight());
        if (dto.getEnabled() != null) existing.setEnabled(dto.getEnabled());
        if (dto.getSort() != null) existing.setSort(dto.getSort());
        emojiItemMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deletePack(Long id) {
        if (id == null) return;
        emojiPackMapper.deleteById(id);
    }

    @Override
    public void deleteItem(Long id) {
        if (id == null) return;
        emojiItemMapper.deleteById(id);
    }
}

