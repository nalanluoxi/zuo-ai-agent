package com.example.authservice.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.example.authservice.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleNotLogin(NotLoginException e) {
        return Map.of("success", false, "message", "未登录或登录已过期", "code", 401);
    }

    @ExceptionHandler(BusinessException.class)
    public Map<String, Object> handleBusiness(BusinessException e) {
        return Map.of("success", false, "message", e.getMessage(), "code", e.getCode());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception e) {
        return Map.of("success", false, "message", "服务器内部错误", "code", 500);
    }
}
