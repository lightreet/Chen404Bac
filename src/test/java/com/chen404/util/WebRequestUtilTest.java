package com.chen404.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebRequestUtilTest {

    @Test
    void shouldPreferFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.10, 10.0.0.2 ");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("203.0.113.10", WebRequestUtil.getClientIp(request));
    }

    @Test
    void shouldFallbackToRealIpAndNormalizeLoopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "::1");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("127.0.0.1", WebRequestUtil.getClientIp(request));
    }

    @Test
    void shouldFallbackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");

        assertEquals("198.51.100.7", WebRequestUtil.getClientIp(request));
    }
}
