package com.chen404.service.impl;

import com.chen404.domain.enums.VerificationCodeTypeEnum;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.service.EmailService;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceImplTest {

    @Test
    void shouldReserveIntervalAndDailyQuotaBeforeSending() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        EmailService emailService = mock(EmailService.class);
        VerificationCodeServiceImpl service = new VerificationCodeServiceImpl(redisUtil, emailService);
        String target = "user@example.com";

        when(redisUtil.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisUtil.incrementWithInitialTtl(anyString(), any(Duration.class))).thenReturn(1L);

        service.generateAndSendCode(target, VerificationCodeTypeEnum.REGISTER);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(
                org.mockito.ArgumentMatchers.eq(target),
                codeCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(VerificationCodeTypeEnum.REGISTER)
        );
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
    }

    @Test
    void shouldRejectUnsupportedPhoneChannel() {
        VerificationCodeServiceImpl service = new VerificationCodeServiceImpl(
                mock(RedisUtil.class),
                mock(EmailService.class)
        );

        assertThrows(
                BadRequestException.class,
                () -> service.generateAndSendCode("13800138000", VerificationCodeTypeEnum.REGISTER)
        );
    }

    @Test
    void shouldAtomicallyConsumeValidCodeAndLockAfterTooManyFailures() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        VerificationCodeServiceImpl service = new VerificationCodeServiceImpl(
                redisUtil,
                mock(EmailService.class)
        );
        String target = "user@example.com";
        String codeKey = RedisKeys.verifyCode(VerificationCodeTypeEnum.RESET, target);
        String attemptsKey = RedisKeys.verifyAttempts(VerificationCodeTypeEnum.RESET, target);

        when(redisUtil.verifyAndConsumeCode(
                codeKey,
                attemptsKey,
                "123456",
                5,
                Duration.ofMinutes(5)
        )).thenReturn(1L);
        when(redisUtil.verifyAndConsumeCode(
                codeKey,
                attemptsKey,
                "000000",
                5,
                Duration.ofMinutes(5)
        )).thenReturn(-2L);

        assertTrue(service.verifyCode(target, VerificationCodeTypeEnum.RESET, "123456"));
        assertFalse(service.verifyCode(target, VerificationCodeTypeEnum.RESET, "000000"));
    }

    @Test
    void shouldRejectSendWhenDailyQuotaIsExceeded() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        VerificationCodeServiceImpl service = new VerificationCodeServiceImpl(
                redisUtil,
                mock(EmailService.class)
        );
        when(redisUtil.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisUtil.incrementWithInitialTtl(anyString(), any(Duration.class))).thenReturn(11L);

        assertThrows(
                TooManyRequestsException.class,
                () -> service.generateAndSendCode("user@example.com", VerificationCodeTypeEnum.REGISTER)
        );
    }
}
