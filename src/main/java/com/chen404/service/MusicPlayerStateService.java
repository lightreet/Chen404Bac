package com.chen404.service;

import com.chen404.domain.dto.MusicPlayerStateCommand;
import com.chen404.domain.dto.MusicPlayerStateVO;

/**
 * 音乐播放现场临时持久化服务。
 */
public interface MusicPlayerStateService {

    /**
     * 获取登录用户最近一次播放现场，不存在或已过期时返回空。
     *
     * @param userId 用户 ID
     * @return 播放现场
     */
    MusicPlayerStateVO getState(Long userId);

    /**
     * 覆盖保存登录用户播放现场，并刷新过期时间。
     *
     * @param userId 用户 ID
     * @param command 播放现场
     */
    void saveState(Long userId, MusicPlayerStateCommand command);

    /**
     * 清除登录用户播放现场。
     *
     * @param userId 用户 ID
     */
    void clearState(Long userId);
}
