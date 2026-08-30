package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.example.authservice.entity.UserDO;
import com.example.authservice.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("")
public class AuthController {
    private final AuthService authService;

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        try {
            SaTokenInfo tokenInfo = authService.register(body.get("username"), body.get("password"), body.get("nickname"));
            UserDO user = authService.currentUser();
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", tokenInfo.tokenValue);
            data.put("loginId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            
            return buildResponse(0, "ok", data);
        } catch (Exception e) {
            return buildResponse(1, e.getMessage(), null);
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            SaTokenInfo tokenInfo = authService.login(body.get("username"), body.get("password"), request);
            UserDO user = authService.currentUser();
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", tokenInfo.tokenValue);
            data.put("loginId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            
            return buildResponse(0, "ok", data);
        } catch (Exception e) {
            return buildResponse(1, e.getMessage(), null);
        }
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public Map<String, Object> logout() {
        try {
            authService.logout();
            return buildResponse(0, "ok", null);
        } catch (Exception e) {
            return buildResponse(1, e.getMessage(), null);
        }
    }

    @GetMapping("/me")
    @SaCheckLogin
    public Map<String, Object> me() {
        try {
            UserDO user = authService.currentUser();
            List<String> roleCodes = authService.getUserRoleCodes(user.getId());
            
            Map<String, Object> data = new HashMap<>();
            data.put("loginId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("email", user.getEmail());
            data.put("tenantId", user.getTenantId());
            data.put("roles", roleCodes);
            data.put("permissions", roleCodes); // 兼容前端字段名
            
            return buildResponse(0, "ok", data);
        } catch (Exception e) {
            return buildResponse(1, e.getMessage(), null);
        }
    }

    private Map<String, Object> buildResponse(int code, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("data", data);
        return response;
    }
}