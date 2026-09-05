package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.UserDO;
import com.example.authservice.service.PageAuthHelper;
import com.example.authservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    /** 用户管理归属"租户管理"页面 */
    private static final String PAGE_CODE = "manage:tenants";

    private final UserService userService;
    private final PageAuthHelper pageAuthHelper;

    @GetMapping("/page")
    @SaCheckLogin
    public Page<UserDO> page(@RequestParam(defaultValue = "1") int current,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String keyword) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return userService.page(current, size, keyword);
    }

    /**
     * 成员管理列表（含部门归属与角色）
     */
    @GetMapping("/manage-list")
    @SaCheckLogin
    public List<Map<String, Object>> manageList(@RequestParam(required = false) String keyword) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return userService.manageList(keyword);
    }

    /**
     * 查询用户归属的部门 id 列表（设置部门归属弹窗回显用）
     */
    @GetMapping("/{id}/departments")
    @SaCheckLogin
    public List<Long> getUserDepartments(@PathVariable Long id) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return userService.getUserDepartmentIds(id);
    }
    @PostMapping("/{id}/department")
    @SaCheckLogin
    public void setDepartment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        Object teamIdsObj = body.get("teamIds");
        List<Long> teamIds = null;
        if (teamIdsObj instanceof List<?> list) {
            teamIds = list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        }
        userService.syncDepartments(id, teamIds);
    }

    /**
     * 将用户移出指定部门（负责人身份不可移除）
     */
    @DeleteMapping("/{id}/teams/{teamId}")
    @SaCheckLogin
    public void removeFromTeam(@PathVariable Long id, @PathVariable Long teamId) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        userService.removeFromTeam(id, teamId);
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public UserDO getById(@PathVariable Long id) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return userService.getById(id);
    }

    @PostMapping
    @SaCheckLogin
    public UserDO create(@RequestBody Map<String, Object> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        return userService.create(
                body.get("username").toString(),
                body.get("password").toString(),
                body.get("nickname").toString(),
                Long.valueOf(body.get("tenantId").toString()));
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    public void update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        userService.update(id,
                (String) body.get("nickname"),
                (String) body.get("email"),
                body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : null);
    }

    @PostMapping("/{id}/roles")
    @SaCheckLogin
    public void assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        pageAuthHelper.checkWrite(PAGE_CODE);
        userService.assignRoles(id, body.get("roleIds"));
    }

    @GetMapping("/{id}/roles")
    @SaCheckLogin
    public List<Long> getUserRoles(@PathVariable Long id) {
        pageAuthHelper.checkRead(PAGE_CODE);
        return userService.getUserRoles(id);
    }

    /**
     * 修改当前登录用户密码
     */
    @PostMapping("/change-password")
    @SaCheckLogin
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return Map.of("success", false, "message", "旧密码和新密码不能为空");
        }

        Long currentUserId = StpUtil.getLoginIdAsLong();
        boolean success = userService.changePassword(currentUserId, oldPassword, newPassword);

        if (success) {
            return Map.of("success", true, "message", "密码修改成功");
        } else {
            return Map.of("success", false, "message", "旧密码错误");
        }
    }

    /**
     * 修改当前登录用户昵称
     */
    @PostMapping("/change-nickname")
    @SaCheckLogin
    public Map<String, Object> changeNickname(@RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");

        if (nickname == null || nickname.trim().isEmpty()) {
            return Map.of("success", false, "message", "昵称不能为空");
        }

        Long currentUserId = StpUtil.getLoginIdAsLong();
        userService.updateNickname(currentUserId, nickname);

        return Map.of("success", true, "message", "昵称修改成功");
    }
}