package com.chen404.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateTrustRequestDTO {

    private String reason;

    private List<String> attachmentUrls;
}
