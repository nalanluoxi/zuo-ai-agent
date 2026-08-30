package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.PermissionDO;
import com.example.authservice.entity.RoleDO;
import com.example.authservice.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping("/page")
    @SaCheckLogin
    public Page<RoleDO> page(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "10") int size) {
        return roleService.page(current, size);
    }

    @GetMapping("/all")
    @SaCheckLogin
    public List<RoleDO> listAll() { return roleService.listAll(); }

    @PostMapping
    @SaCheckLogin
    public RoleDO create(@RequestBody Map<String, String> body) {
        return roleService.create(body.get("roleCode"), body.get("roleName"),
                body.get("scopeType"), body.get("dataScope"));
    }

    @PostMapping("/{id}/permissions")
    @SaCheckLogin
    public void assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permIds"));
    }

    @GetMapping("/{id}/permissions")
    @SaCheckLogin
    public List<PermissionDO> getPermissions(@PathVariable Long id) {
        return roleService.getRolePermissions(id);
    }
}