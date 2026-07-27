package com.chen404.service;

import com.chen404.domain.event.AdminContentEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 统一发布会进入管理员消息中心的业务事件。
 */
@Component
public class AdminContentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public AdminContentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(AdminContentEvent event) {
        if (event != null) {
            applicationEventPublisher.publishEvent(event);
        }
    }
}
