package com.chen404.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;

import java.util.List;

public interface EmojiService {

    Page<EmojiPack> pageAllPacks(int page, int size);

    List<EmojiPack> listAllPacks();

    List<EmojiPack> listEnabledPacks();

    Page<EmojiItem> pageAllItems(int page, int size, String packCode);

    List<EmojiItem> listAllItems(String packCode);

    List<EmojiItem> listEnabledItems(String scene, String packCode);

    EmojiPack upsertPack(EmojiPackUpsertDTO dto);

    EmojiItem upsertItem(EmojiItemUpsertDTO dto);

    void deletePack(Long id);

    void deleteItem(Long id);
}

