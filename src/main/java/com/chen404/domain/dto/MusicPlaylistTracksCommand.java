package com.chen404.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MusicPlaylistTracksCommand {

    private List<Long> trackIds = new ArrayList<>();
}
