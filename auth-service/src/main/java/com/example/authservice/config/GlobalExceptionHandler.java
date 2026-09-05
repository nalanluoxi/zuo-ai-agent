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
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        // 业务异常返回真实 HTTP 状态码，前端 axios 走 reject 分支才能弹出失败原因
        HttpStatus status = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return org.springframework.http.ResponseEntity.status(status)
                .body(Map.of("success", false, "message", e.getMessage(), "code", e.getCode()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception e) {
        return Map.of("success", false, "message", "服务器内部错误", "code", 500);
    }
}
