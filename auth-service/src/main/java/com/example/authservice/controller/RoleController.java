package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.PermissionDO;
import com.example.authservice.entity.RoleDO;
import com.example.authservice.service.PageAuthHelper;
import com.example.authservice.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    /** 角色管理归属"租户管理"页面 */
    private static final String PAGE_CODE = "manage:tenants";

    private final RoleService roleService;
    private final PageAuthHelper pageAuthHelper;

    @GetMapping("/page")
    @SaCheckLogin
    public Page<RoleDO> page(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "10") int size) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.page(current, size);
    }

    @GetMapping("/all")
    @SaCheckLogin
    public List<RoleDO> listAll() {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.listAll();
    }

    @PostMapping
    @SaCheckLogin
    public RoleDO create(@RequestBody Map<String, String> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        return roleService.create(body.get("roleCode"), body.get("roleName"),
                body.get("scopeType"), body.get("dataScope"));
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public void delete(@PathVariable Long id) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        roleService.deleteRole(id);
    }

    @PostMapping("/{id}/permissions")
    @SaCheckLogin
    public void assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        roleService.assignPermissions(id, body.get("permIds"));
    }

    @GetMapping("/{id}/permissions")
    @SaCheckLogin
    public List<PermissionDO> getPermissions(@PathVariable Long id) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.getRolePermissions(id);
    }

    // ═══════════════ 页面级权限配置 ═══════════════

    /**
     * 页面清单（可配置的页面列表）
     */
    @GetMapping("/page-list")
    @SaCheckLogin
    public List<PermissionDO> listPages() {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.listPages();
    }

    /**
     * 角色列表（含页面权限摘要，用于角色权限 tab 展示）
     */
    @GetMapping("/with-page-perms")
    @SaCheckLogin
    public List<Map<String, Object>> listWithPagePerms() {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.listWithPagePerms();
    }

    /**
     * 查询角色的页面权限（含访问级别）
     */
    @GetMapping("/{id}/page-permissions")
    @SaCheckLogin
    public List<Map<String, Object>> getRolePagePermissions(@PathVariable Long id) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return roleService.getRolePagePermissions(id);
    }

    /**
     * 保存角色的页面权限（全量替换）
     * body: { "items": [{ "permissionId": 101, "accessLevel": "READ" }] }
     */
    @PostMapping("/{id}/page-permissions")
    @SaCheckLogin
    public void saveRolePagePermissions(@PathVariable Long id, @RequestBody Map<String, List<Map<String, Object>>> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        roleService.saveRolePagePermissions(id, body.get("items"));
    }
}