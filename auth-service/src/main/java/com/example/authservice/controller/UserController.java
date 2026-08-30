package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.UserDO;
import com.example.authservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/page")
    @SaCheckLogin
    public Page<UserDO> page(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String keyword) {
        return userService.page(current, size, keyword);
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public UserDO getById(@PathVariable Long id) { return userService.getById(id); }

    @PostMapping
    @SaCheckLogin
    public UserDO create(@RequestBody Map<String, Object> body) {
        return userService.create(
                body.get("username").toString(),
                body.get("password").toString(),
                body.get("nickname").toString(),
                Long.valueOf(body.get("tenantId").toString()));
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    public void update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        userService.update(id,
                (String) body.get("nickname"),
                (String) body.get("email"),
                body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : null);
    }

    @PostMapping("/{id}/roles")
    @SaCheckLogin
    public void assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
    }

    @GetMapping("/{id}/roles")
    @SaCheckLogin
    public List<Long> getUserRoles(@PathVariable Long id) { return userService.getUserRoles(id); }
}