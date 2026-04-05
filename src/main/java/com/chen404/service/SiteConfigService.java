package com.chen404.service;

import com.chen404.domain.dto.SiteConfigDTO;

public interface SiteConfigService {

    SiteConfigDTO getConfig();

    SiteConfigDTO updateConfig(SiteConfigDTO patch);
}
