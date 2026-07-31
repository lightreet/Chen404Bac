package com.chen404.exception;

import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void bindExceptionShouldReturnUnprocessableEntity() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "分类名称不能为空"));
        BindException exception = new BindException(bindingResult);

        ResponseEntity<Result<String>> response = handler.handleBindException(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiErrorCode.VALIDATION_FAILED, response.getBody().getCode());
        assertEquals("分类名称不能为空", response.getBody().getMessage());
    }

    @Test
    void resourceNotFoundShouldReturnNotFound() {
        ResponseEntity<Result<String>> response =
                handler.handleApiException(new ResourceNotFoundException("分类不存在"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiErrorCode.NOT_FOUND, response.getBody().getCode());
        assertEquals("分类不存在", response.getBody().getMessage());
    }

    @Test
    void badRequestExceptionShouldCarryStatusAndCode() {
        ResponseEntity<Result<String>> response =
                handler.handleApiException(new BadRequestException("请求参数错误"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiErrorCode.BAD_REQUEST, response.getBody().getCode());
        assertEquals("请求参数错误", response.getBody().getMessage());
    }

    @Test
    void conflictExceptionShouldReturnConflict() {
        ResponseEntity<Result<String>> response =
                handler.handleApiException(new ConflictException("资源状态已变化"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiErrorCode.CONFLICT, response.getBody().getCode());
    }

    @Test
    void illegalArgumentShouldReturnBadRequest() {
        ResponseEntity<Result<String>> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("参数不合法"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApiErrorCode.BAD_REQUEST, response.getBody().getCode());
    }
}
