package com.chen404.service.support.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI prompt 模板加载器。
 * <p>
 * 统一负责从 resources 或外部资源路径读取 prompt 模板，并在进程内做轻量缓存，
 * 避免业务层反复处理资源定位与字符集细节。
 */
@Component
public class AiPromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(AiPromptTemplateLoader.class);

    private static final String EMPTY_TEMPLATE_ERROR = "AI prompt 模板内容不能为空";
    private static final String MISSING_TEMPLATE_ERROR = "AI prompt 模板不存在";
    private static final String LOAD_TEMPLATE_ERROR = "AI prompt 模板读取失败";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public AiPromptTemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载必需的 prompt 模板。
     *
     * @param location 模板资源位置
     * @return 模板内容
     */
    public String loadRequiredTemplate(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("AI prompt 模板位置不能为空");
        }
        return templateCache.computeIfAbsent(location.trim(), this::doLoadTemplate);
    }

    private String doLoadTemplate(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.error("[AI_PROMPT_TEMPLATE_MISSING] location={}", location);
            throw new IllegalStateException(MISSING_TEMPLATE_ERROR + "，location=" + location);
        }

        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
            if (!StringUtils.hasText(content)) {
                log.error("[AI_PROMPT_TEMPLATE_EMPTY] location={}", location);
                throw new IllegalStateException(EMPTY_TEMPLATE_ERROR + "，location=" + location);
            }
            log.info("[AI_PROMPT_TEMPLATE_LOADED] location={} length={}", location, content.length());
            return content;
        } catch (IOException e) {
            log.error("[AI_PROMPT_TEMPLATE_LOAD_FAIL] location={} message={}", location, e.getMessage(), e);
            throw new IllegalStateException(LOAD_TEMPLATE_ERROR + "，location=" + location, e);
        }
    }
}
