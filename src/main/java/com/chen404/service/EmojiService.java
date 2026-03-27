package com.chen404.service;

import com.chen404.domain.dto.EmojiItemUpsertDTO;
import com.chen404.domain.dto.EmojiPackUpsertDTO;
import com.chen404.domain.entity.EmojiItem;
import com.chen404.domain.entity.EmojiPack;

import java.util.List;

public interface EmojiService {

    List<EmojiPack> listEnabledPacks();

    List<EmojiItem> listEnabledItems(String scene, String packCode);

    EmojiPack upsertPack(EmojiPackUpsertDTO dto);

    EmojiItem upsertItem(EmojiItemUpsertDTO dto);

    void deletePack(Long id);

    void deleteItem(Long id);
}

