package com.example.authservice.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserTeamMapper userTeamMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final TeamMapper teamMapper;

    public Page<UserDO> page(int current, int size, String keyword) {
        LambdaQueryWrapper<UserDO> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UserDO::getUsername, keyword).or().like(UserDO::getNickname, keyword);
        }
        qw.orderByDesc(UserDO::getCreateTime);
        return userMapper.selectPage(new Page<>(current, size), qw);
    }

    public UserDO getById(Long id) { return userMapper.selectById(id); }

    @Transactional
    public UserDO create(String username, String password, String nickname, Long tenantId) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname);
        user.setTenantId(tenantId);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public void update(Long id, String nickname, String email, Integer status) {
        UserDO user = userMapper.selectById(id);
        if (user == null) throw new RuntimeException("用户不存在");
        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);
        if (status != null) user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId); ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    /**
     * 分配默认角色（普通用户 USER），注册时调用；角色不存在时跳过不影响注册
     */
    @Transactional
    public void assignDefaultRole(Long userId) {
        RoleDO userRole = roleMapper.selectOne(
                new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getRoleCode, "USER"));
        if (userRole == null) return;
        UserRoleDO ur = new UserRoleDO();
        ur.setUserId(userId);
        ur.setRoleId(userRole.getId());
        userRoleMapper.insert(ur);
    }

    public List<Long> getUserRoles(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId))
                .stream().map(UserRoleDO::getRoleId).toList();
    }

    /**
     * 获取用户的角色编码列表（用于前端权限判断）
     */
    public List<String> getUserRoleCodes(Long userId) {
        List<Long> roleIds = getUserRoles(userId);
        if (roleIds.isEmpty()) return List.of();

        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().in(RoleDO::getId, roleIds))
                .stream().map(RoleDO::getRoleCode).toList();
    }

    /**
     * 获取用户的页面权限（多角色合并，同页面取最高级别）
     * 所有角色（含 SUPER_ADMIN）统一从 t_role_permission 配置读取
     *
     * @return [{pageCode, accessLevel}]，accessLevel ∈ READ/WRITE/ADMIN
     */
    public List<Map<String, String>> getUserPagePermissions(Long userId) {
        List<PermissionDO> pages = permissionMapper.selectList(
                new LambdaQueryWrapper<PermissionDO>().eq(PermissionDO::getResourceType, "PAGE"));
        List<Long> roleIds = getUserRoles(userId);
        if (roleIds.isEmpty() || pages.isEmpty()) return List.of();

        List<RolePermissionDO> rps = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermissionDO>().in(RolePermissionDO::getRoleId, roleIds));
        Map<Long, String> permCodeMap = new HashMap<>();
        pages.forEach(p -> permCodeMap.put(p.getId(), p.getPermCode()));

        Map<String, String> merged = new HashMap<>();
        for (RolePermissionDO rp : rps) {
            String pageCode = permCodeMap.get(rp.getPermissionId());
            if (pageCode == null) continue;
            String level = rp.getAccessLevel() == null ? "READ" : rp.getAccessLevel();
            merged.merge(pageCode, level, UserService::maxLevel);
        }
        return merged.entrySet().stream()
                .map(e -> Map.of("pageCode", e.getKey(), "accessLevel", e.getValue()))
                .toList();
    }

    /** 级别比较：READ < WRITE < ADMIN */
    private static String maxLevel(String a, String b) {
        return levelRank(a) >= levelRank(b) ? a : b;
    }

    private static int levelRank(String level) {
        return switch (level) {
            case "ADMIN" -> 3;
            case "WRITE" -> 2;
            default -> 1;
        };
    }

    /**
     * 成员管理列表：用户 + 部门归属（含完整层级路径）+ 拥有角色
     *
     * @return [{id, username, nickname, status, teamNames, teams:[{teamId, teamName, teamPath, roleInTeam}], roles:[{roleId, roleName}]}]
     */
    public List<Map<String, Object>> manageList(String keyword) {
        LambdaQueryWrapper<UserDO> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(UserDO::getUsername, keyword).or().like(UserDO::getNickname, keyword));
        }
        qw.orderByAsc(UserDO::getId);
        List<UserDO> users = userMapper.selectList(qw);

        List<UserTeamDO> allUserTeams = userTeamMapper.selectList(new LambdaQueryWrapper<>());
        List<TeamDO> allTeams = teamMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, TeamDO> teamMap = new HashMap<>();
        allTeams.forEach(t -> teamMap.put(t.getId(), t));

        List<RoleDO> allRoles = roleMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, String> roleNameMap = new HashMap<>();
        allRoles.forEach(r -> roleNameMap.put(r.getId(), r.getRoleName()));
        List<UserRoleDO> allUserRoles = userRoleMapper.selectList(new LambdaQueryWrapper<>());

        return users.stream().map(u -> {
            List<Map<String, Object>> teams = allUserTeams.stream()
                    .filter(ut -> ut.getUserId().equals(u.getId()))
                    .filter(ut -> teamMap.containsKey(ut.getTeamId()))
                    .filter(ut -> isChainActive(teamMap.get(ut.getTeamId()), teamMap)) // 下线部门（含祖先下线）不显示
                    .map(ut -> {
                        TeamDO team = teamMap.get(ut.getTeamId());
                        return Map.<String, Object>of(
                                "teamId", ut.getTeamId(),
                                "teamName", team.getTeamName(),
                                "teamPath", buildTeamPath(team, teamMap),
                                "roleInTeam", ut.getRoleInTeam() == null ? "MEMBER" : ut.getRoleInTeam());
                    })
                    .toList();
            // 归属去重（业内通行口径）：祖先部门已有归属时，下级归属不单独显示（隐含被覆盖）
            List<Map<String, Object>> displayTeams = filterTopMostMemberships(teams, teamMap);
            List<String> teamNames = displayTeams.stream().map(t -> (String) t.get("teamName")).toList();
            List<String> teamPaths = displayTeams.stream().map(t -> (String) t.get("teamPath")).toList();
            List<Map<String, Object>> roles = allUserRoles.stream()
                    .filter(ur -> ur.getUserId().equals(u.getId()))
                    .map(ur -> Map.<String, Object>of(
                            "roleId", ur.getRoleId(),
                            "roleName", roleNameMap.getOrDefault(ur.getRoleId(), "未知角色")))
                    .toList();
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("nickname", u.getNickname());
            item.put("status", u.getStatus());
            item.put("teamNames", teamNames);
            item.put("teamPaths", teamPaths);
            item.put("teams", teams);
            item.put("roles", roles);
            return item;
        }).toList();
    }

    /**
     * 归属去重：只保留"最高归属点"——某归属部门的任意祖先也在归属集合中时，该归属被覆盖不显示
     */
    private List<Map<String, Object>> filterTopMostMemberships(List<Map<String, Object>> teams, Map<Long, TeamDO> teamMap) {
        java.util.Set<Long> memberIds = teams.stream()
                .map(t -> Long.valueOf(String.valueOf(t.get("teamId"))))
                .collect(java.util.stream.Collectors.toSet());
        return teams.stream().filter(t -> {
            Long teamId = Long.valueOf(String.valueOf(t.get("teamId")));
            TeamDO cur = teamMap.get(teamId);
            java.util.Set<Long> visited = new java.util.HashSet<>();
            while (cur != null && cur.getParentId() != null && cur.getParentId() != 0 && visited.add(cur.getId())) {
                if (memberIds.contains(cur.getParentId())) {
                    return false; // 祖先已有归属，本条被覆盖
                }
                cur = teamMap.get(cur.getParentId());
            }
            return true;
        }).toList();
    }

    /**
     * 部门链是否有效：自身或任一祖先已下线（status=0）则视为无效，归属路径不展示
     */
    private boolean isChainActive(TeamDO team, Map<Long, TeamDO> teamMap) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        TeamDO cur = team;
        while (cur != null && visited.add(cur.getId())) {
            if (cur.getStatus() != null && cur.getStatus() == 0) {
                return false;
            }
            Long pid = cur.getParentId();
            cur = (pid == null || pid == 0) ? null : teamMap.get(pid);
        }
        return true;
    }

    /**
     * 构建团队的完整层级路径（顶级部门 - ... - 当前部门）
     */
    private String buildTeamPath(TeamDO team, Map<Long, TeamDO> teamMap) {
        List<String> names = new ArrayList<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        TeamDO cur = team;
        while (cur != null && visited.add(cur.getId())) {
            names.add(0, cur.getTeamName());
            cur = cur.getParentId() != null ? teamMap.get(cur.getParentId()) : null;
        }
        return String.join(" - ", names);
    }

    /**
     * 获取用户的角色名称列表（用于"我的信息"展示）
     */
    public List<String> getUserRoleNames(Long userId) {
        List<Long> roleIds = getUserRoles(userId);
        if (roleIds.isEmpty()) return List.of();
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>().in(RoleDO::getId, roleIds))
                .stream().map(RoleDO::getRoleName).toList();
    }

    /**
     * 获取用户隶属部门的完整层级路径列表（"我的信息"展示用，已按"最高归属点"去重）
     */
    public List<String> getUserTeamPaths(Long userId) {
        List<UserTeamDO> userTeams = userTeamMapper.selectList(
                new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getUserId, userId));
        if (userTeams.isEmpty()) return List.of();
        List<TeamDO> allTeams = teamMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, TeamDO> teamMap = new HashMap<>();
        allTeams.forEach(t -> teamMap.put(t.getId(), t));
        List<Map<String, Object>> teams = userTeams.stream()
                .filter(ut -> teamMap.containsKey(ut.getTeamId()))
                .filter(ut -> isChainActive(teamMap.get(ut.getTeamId()), teamMap)) // 下线部门（含祖先下线）不显示
                .map(ut -> {
                    TeamDO team = teamMap.get(ut.getTeamId());
                    Map<String, Object> m = new HashMap<>();
                    m.put("teamId", ut.getTeamId());
                    m.put("teamPath", buildTeamPath(team, teamMap));
                    return m;
                })
                .toList();
        return filterTopMostMemberships(teams, teamMap).stream()
                .map(t -> (String) t.get("teamPath"))
                .toList();
    }

    /**
     * 获取用户的部门归属（含部门内角色），"我的信息"/前端权限判断用
     *
     * @return [{teamId, teamName, roleInTeam}]
     */
    public List<Map<String, Object>> getUserTeams(Long userId) {
        List<UserTeamDO> userTeams = userTeamMapper.selectList(
                new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getUserId, userId));
        if (userTeams.isEmpty()) return List.of();
        List<TeamDO> teams = teamMapper.selectBatchIds(
                userTeams.stream().map(UserTeamDO::getTeamId).toList());
        Map<Long, String> nameMap = new HashMap<>();
        teams.forEach(t -> nameMap.put(t.getId(), t.getTeamName()));
        return userTeams.stream()
                .filter(ut -> nameMap.containsKey(ut.getTeamId()))
                .map(ut -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("teamId", ut.getTeamId());
                    m.put("teamName", nameMap.get(ut.getTeamId()));
                    m.put("roleInTeam", ut.getRoleInTeam() == null ? "MEMBER" : ut.getRoleInTeam());
                    return m;
                })
                .toList();
    }

    /**
     * 获取用户归属的部门 id 列表（设置部门归属弹窗回显用，实时查询）
     */
    public List<Long> getUserDepartmentIds(Long userId) {
        return userTeamMapper.selectList(
                        new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getUserId, userId))
                .stream().map(UserTeamDO::getTeamId).toList();
    }

    /**
     * 设置用户的部门归属：全量同步为目标部门列表（勾选=加入，未勾选=移出）
     */
    @Transactional
    public void syncDepartments(Long userId, List<Long> teamIds) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        // 现有归属
        List<UserTeamDO> existing = userTeamMapper.selectList(
                new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getUserId, userId));
        List<Long> targetIds = teamIds == null ? List.of() : teamIds;

        // 移出：现有但不在目标列表中的
        List<Long> toRemove = existing.stream()
                .map(UserTeamDO::getTeamId)
                .filter(tid -> !targetIds.contains(tid))
                .toList();
        if (!toRemove.isEmpty()) {
            userTeamMapper.delete(new LambdaQueryWrapper<UserTeamDO>()
                    .eq(UserTeamDO::getUserId, userId)
                    .in(UserTeamDO::getTeamId, toRemove));
        }

        // 加入：目标列表中但还未加入的
        List<Long> existingIds = existing.stream().map(UserTeamDO::getTeamId).toList();
        for (Long teamId : targetIds) {
            if (teamId == null || existingIds.contains(teamId)) continue;
            if (teamMapper.selectById(teamId) == null) continue;
            UserTeamDO ut = new UserTeamDO();
            ut.setUserId(userId);
            ut.setTeamId(teamId);
            ut.setRoleInTeam("MEMBER");
            userTeamMapper.insert(ut);
        }
    }

    /**
     * 将用户移出指定团队（管理员操作，含负责人身份也可移除）
     */
    @Transactional
    public void removeFromTeam(Long userId, Long teamId) {
        long deleted = userTeamMapper.delete(new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getUserId, userId)
                .eq(UserTeamDO::getTeamId, teamId));
        if (deleted == 0) {
            throw new com.example.authservice.common.BusinessException("移除失败：用户不在该部门");
        }
    }

    /**
     * 修改密码
     */
    @Transactional
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) return false;

        // 验证旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            return false;
        }

        // 更新新密码
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userMapper.updateById(user);
        return true;
    }

    /**
     * 修改昵称
     */
    @Transactional
    public void updateNickname(Long userId, String nickname) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        user.setNickname(nickname);
        userMapper.updateById(user);
    }
}