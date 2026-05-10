package com.chen404.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 女仆人设与 prompt 资源配置。
 * <p>
 * 该配置只负责描述角色名、版本号与 prompt 模板位置，
 * 便于后续在不修改业务代码的前提下切换人设文案或任务模板。
 */
@Component
@ConfigurationProperties(prefix = "app.ai.maid")
public class AiMaidProperties {

    private static final String DEFAULT_NAME = "Lyra";
    private static final String DEFAULT_PERSONA_VERSION = "v1.1";
    private static final String DEFAULT_SYSTEM_PROMPT_LOCATION = "classpath:prompts/ai/maid-system-prompt.txt";
    private static final String DEFAULT_HELPER_TASK_PROMPT_LOCATION = "classpath:prompts/ai/maid-helper-task-prompt.txt";
    private static final String DEFAULT_COMPANION_TASK_PROMPT_LOCATION = "classpath:prompts/ai/maid-companion-task-prompt.txt";

    private String name = DEFAULT_NAME;
    private String personaVersion = DEFAULT_PERSONA_VERSION;
    private String systemPromptLocation = DEFAULT_SYSTEM_PROMPT_LOCATION;
    private String helperTaskPromptLocation = DEFAULT_HELPER_TASK_PROMPT_LOCATION;
    private String companionTaskPromptLocation = DEFAULT_COMPANION_TASK_PROMPT_LOCATION;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPersonaVersion() {
        return personaVersion;
    }

    public void setPersonaVersion(String personaVersion) {
        this.personaVersion = personaVersion;
    }

    public String getSystemPromptLocation() {
        return systemPromptLocation;
    }

    public void setSystemPromptLocation(String systemPromptLocation) {
        this.systemPromptLocation = systemPromptLocation;
    }

    public String getHelperTaskPromptLocation() {
        return helperTaskPromptLocation;
    }

    public void setHelperTaskPromptLocation(String helperTaskPromptLocation) {
        this.helperTaskPromptLocation = helperTaskPromptLocation;
    }

    public String getCompanionTaskPromptLocation() {
        return companionTaskPromptLocation;
    }

    public void setCompanionTaskPromptLocation(String companionTaskPromptLocation) {
        this.companionTaskPromptLocation = companionTaskPromptLocation;
    }
}
