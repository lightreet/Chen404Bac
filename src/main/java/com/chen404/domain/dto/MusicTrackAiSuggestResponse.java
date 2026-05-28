package com.chen404.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐曲目信息 AI 补全结果。
 */
@Data
public class MusicTrackAiSuggestResponse {

    private List<MusicTrackAiCandidate> candidates = new ArrayList<>();
}
