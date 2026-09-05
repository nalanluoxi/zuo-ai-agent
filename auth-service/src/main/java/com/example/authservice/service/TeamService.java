package com.example.authservice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.authservice.entity.*;
import com.example.authservice.mapper.*;
import com.example.authservice.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {
    private final TeamMapper teamMapper;
    private final UserTeamMapper userTeamMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    
    // 树形缓存
    private static final Map<Long, List<TeamDO>> treeCache = new ConcurrentHashMap<>();
    private static final Map<Long, TeamDO> nodeCache = new ConcurrentHashMap<>();

    public List<TeamDO> listByTenant(Long tenantId) {
        return teamMapper.selectList(new LambdaQueryWrapper<TeamDO>().eq(TeamDO::getTenantId, tenantId));
    }

    public List<TeamDO> listMyTeams() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> teamIds = userTeamMapper.selectList(new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getUserId, userId))
                .stream().map(UserTeamDO::getTeamId).toList();
        return teamIds.isEmpty() ? List.of() : teamMapper.selectBatchIds(teamIds);
    }

    @Transactional
    public TeamDO create(String name, Long tenantId, Long parentId) {
        TeamDO team = new TeamDO();
        team.setTeamName(name); team.setTenantId(tenantId);
        team.setParentId(parentId);
        team.setOwnerId(StpUtil.getLoginIdAsLong()); team.setStatus(1);
        teamMapper.insert(team);
        UserTeamDO ut = new UserTeamDO();
        ut.setUserId(StpUtil.getLoginIdAsLong()); ut.setTeamId(team.getId()); ut.setRoleInTeam("OWNER");
        userTeamMapper.insert(ut);
        refreshCache();
        return team;
    }

    public TeamDO getById(Long id) { return teamMapper.selectById(id); }

    public List<Map<String, Object>> getMembers(Long teamId) {
        List<UserTeamDO> userTeams = userTeamMapper.selectList(
            new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getTeamId, teamId));
        if (userTeams.isEmpty()) return List.of();
        
        List<Long> userIds = userTeams.stream().map(UserTeamDO::getUserId).toList();
        List<UserDO> users = userMapper.selectBatchIds(userIds);
        
        // 构建 userId -> roleInTeam 映射
        Map<Long, String> roleMap = new HashMap<>();
        for (UserTeamDO ut : userTeams) {
            roleMap.put(ut.getUserId(), ut.getRoleInTeam());
        }
        
        // 组装返回结果，包含用户信息和角色
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserDO user : users) {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("userId", user.getId());
            member.put("username", user.getUsername());
            member.put("nickname", user.getNickname());
            member.put("email", user.getEmail());
            member.put("status", user.getStatus());
            member.put("roleInTeam", roleMap.getOrDefault(user.getId(), "MEMBER"));
            result.add(member);
        }
        return result;
    }

    @Transactional
    public void addMember(Long teamId, Long userId, String roleInTeam) {
        // P8 修复：权限校验 - 只有 OWNER 和 ADMIN 才能添加成员
        Long currentUserId = StpUtil.getLoginIdAsLong();
        validateTeamPermission(teamId, currentUserId, "OWNER");

        String targetRole = roleInTeam != null ? roleInTeam : "MEMBER";
        UserTeamDO existing = userTeamMapper.selectOne(new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, teamId).eq(UserTeamDO::getUserId, userId));
        if (existing != null) {
            // 已是成员：角色不一致时更新角色（如 MEMBER 升级为 OWNER）
            if (!targetRole.equals(existing.getRoleInTeam())) {
                existing.setRoleInTeam(targetRole);
                userTeamMapper.updateById(existing);
            }
            return;
        }
        UserTeamDO ut = new UserTeamDO();
        ut.setTeamId(teamId); ut.setUserId(userId); ut.setRoleInTeam(targetRole);
        userTeamMapper.insert(ut);
    }

    @Transactional
    public void removeMember(Long teamId, Long userId) {
        // P10 修复：权限校验 - 只有 OWNER 才能移除成员
        Long currentUserId = StpUtil.getLoginIdAsLong();
        validateTeamPermission(teamId, currentUserId, "OWNER");
        
        userTeamMapper.delete(new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, teamId).eq(UserTeamDO::getUserId, userId));
    }

    /**
     * 获取所有团队（组织架构全局可见，不按租户隔离），附带成员摘要
     */
    public List<TeamDO> listAll(Long tenantId) {
        List<TeamDO> teams = teamMapper.selectList(
            new LambdaQueryWrapper<TeamDO>()
                .eq(TeamDO::getDeleted, 0)
                .orderByAsc(TeamDO::getParentId)
                .orderByAsc(TeamDO::getId)
        );
        fillMemberSummary(teams);
        return teams;
    }

    /**
     * 填充每个部门的成员摘要：负责人昵称（黄色标签展示）+ 成员预览（负责人优先，最多 4 个）+ 总人数
     */
    private void fillMemberSummary(List<TeamDO> teams) {
        if (teams.isEmpty()) return;
        List<UserTeamDO> allMemberships = userTeamMapper.selectList(new LambdaQueryWrapper<>());
        if (allMemberships.isEmpty()) {
            teams.forEach(t -> {
                t.setLeaderNames(List.of());
                t.setMemberPreviews(List.of());
                t.setMemberCount(0);
            });
            return;
        }
        List<UserDO> users = userMapper.selectBatchIds(
                allMemberships.stream().map(UserTeamDO::getUserId).distinct().toList());
        Map<Long, String> nicknameMap = new HashMap<>();
        users.forEach(u -> nicknameMap.put(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername()));

        // teamId → 成员（负责人排前）
        Map<Long, List<UserTeamDO>> byTeam = new HashMap<>();
        allMemberships.forEach(ut -> byTeam.computeIfAbsent(ut.getTeamId(), k -> new ArrayList<>()).add(ut));

        for (TeamDO team : teams) {
            List<UserTeamDO> members = byTeam.getOrDefault(team.getId(), List.of());
            List<UserTeamDO> sorted = new ArrayList<>(members);
            sorted.sort((a, b) -> "OWNER".equals(a.getRoleInTeam()) ? ("OWNER".equals(b.getRoleInTeam()) ? 0 : -1)
                    : ("OWNER".equals(b.getRoleInTeam()) ? 1 : 0));
            List<String> leaders = sorted.stream()
                    .filter(m -> "OWNER".equals(m.getRoleInTeam()))
                    .map(m -> nicknameMap.getOrDefault(m.getUserId(), "未知"))
                    .toList();
            List<String> previews = sorted.stream()
                    .map(m -> nicknameMap.getOrDefault(m.getUserId(), "未知"))
                    .limit(4)
                    .toList();
            team.setLeaderNames(leaders);
            team.setMemberPreviews(previews);
            team.setMemberCount(members.size());
        }
    }

    /**
     * 获取所有团队（无租户隔离，仅用于超级管理员）
     * @deprecated 使用 listAll(Long tenantId) 代替
     */
    @Deprecated
    public List<TeamDO> listAll() { 
        return teamMapper.selectList(new LambdaQueryWrapper<TeamDO>().eq(TeamDO::getDeleted, 0)); 
    }

    public void update(TeamDO team) { 
        teamMapper.updateById(team);
        refreshCache();
    }

    @Transactional
    public void delete(Long id) {
        // 检查是否有子部门
        long childCount = teamMapper.selectCount(
            new LambdaQueryWrapper<TeamDO>()
                .eq(TeamDO::getParentId, id)
                .eq(TeamDO::getDeleted, 0)
        );
        if (childCount > 0) {
            throw new BusinessException("该部门下存在 " + childCount + " 个子部门，无法删除，请先删除所有子部门");
        }

        // 有成员时级联移除成员归属（成员本身保留，仅移出该部门）
        long memberCount = userTeamMapper.selectCount(
            new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, id)
        );
        if (memberCount > 0) {
            userTeamMapper.delete(new LambdaQueryWrapper<UserTeamDO>()
                    .eq(UserTeamDO::getTeamId, id));
        }

        teamMapper.deleteById(id);
        refreshCache();
    }

    // ==================== P7 修复：树形结构方法 ====================

    /**
     * P7 修复：获取团队树（按层级返回树形结构）
     */
    public List<TeamDO> getTeamTree(Long tenantId) {
        refreshCache();
        List<TeamDO> allTeams = teamMapper.selectList(
            new LambdaQueryWrapper<TeamDO>()
                .eq(TeamDO::getTenantId, tenantId)
                .eq(TeamDO::getDeleted, 0)
                .orderByAsc(TeamDO::getParentId)
                .orderByAsc(TeamDO::getId)
        );
        
        // 构建树形结构
        Map<Long, List<TeamDO>> childrenMap = new HashMap<>();
        List<TeamDO> rootTeams = new ArrayList<>();
        
        for (TeamDO team : allTeams) {
            if (team.getParentId() == null || team.getParentId() == 0) {
                rootTeams.add(team);
            } else {
                childrenMap.computeIfAbsent(team.getParentId(), k -> new ArrayList<>()).add(team);
            }
        }
        
        // 递归构建树
        for (TeamDO root : rootTeams) {
            buildTeamTree(root, childrenMap);
        }
        
        return rootTeams;
    }

    /**
     * 递归构建树结构（设置子节点到父节点的 children 字段）
     */
    private void buildTeamTree(TeamDO parent, Map<Long, List<TeamDO>> childrenMap) {
        List<TeamDO> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            for (TeamDO child : children) {
                buildTeamTree(child, childrenMap);
            }
        } else {
            parent.setChildren(new ArrayList<>());
        }
    }

    /**
     * P7 修复：添加子部门
     */
    @Transactional
    public TeamDO addSubTeam(Long parentId, String subTeamName, Long tenantId) {
        // 权限校验
        Long currentUserId = StpUtil.getLoginIdAsLong();
        validateTeamPermission(parentId, currentUserId, "OWNER");
        
        TeamDO parent = teamMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("父部门不存在");
        }
        
        // 创建子部门
        TeamDO subTeam = new TeamDO();
        subTeam.setTeamName(subTeamName);
        subTeam.setTenantId(tenantId);
        subTeam.setParentId(parentId);
        subTeam.setOwnerId(currentUserId);
        subTeam.setStatus(1);
        
        teamMapper.insert(subTeam);
        
        // 添加创建者为 OWNER
        UserTeamDO ut = new UserTeamDO();
        ut.setUserId(currentUserId);
        ut.setTeamId(subTeam.getId());
        ut.setRoleInTeam("OWNER");
        userTeamMapper.insert(ut);
        
        refreshCache();
        return subTeam;
    }

    /**
     * P9 修复：设置部门负责人
     */
    @Transactional
    public void setTeamLeader(Long teamId, Long leaderId) {
        // 权限校验 - 只有 OWNER 才能设置负责人
        Long currentUserId = StpUtil.getLoginIdAsLong();
        validateTeamPermission(teamId, currentUserId, "OWNER");
        
        // 验证新负责人是否在团队中
        UserTeamDO userTeam = userTeamMapper.selectOne(
            new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, teamId)
                .eq(UserTeamDO::getUserId, leaderId)
        );
        
        if (userTeam == null) {
            throw new BusinessException("用户不是该团队成员，无法设置为负责人");
        }
        
        // 更新团队负责人
        TeamDO team = new TeamDO();
        team.setId(teamId);
        team.setOwnerId(leaderId);
        teamMapper.updateById(team);
        
        refreshCache();
    }

    /**
     * P7-P10 修复：权限检查方法 - 检查用户是否有权限执行操作
     */
    private void validateTeamPermission(Long teamId, Long userId, String requiredRole) {
        // 拥有"租户管理"页面超级管理（ADMIN）权限的用户可管理所有部门
        if (hasTenantManageAdmin(userId)) {
            return;
        }
        // 权限继承（业内通行）：部门负责人可管辖本部门及所有下级部门
        // 收集本部门及全部祖先部门 id 链
        List<Long> chainIds = new ArrayList<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        TeamDO cur = teamMapper.selectById(teamId);
        while (cur != null && visited.add(cur.getId())) {
            chainIds.add(cur.getId());
            Long pid = cur.getParentId();
            cur = (pid == null || pid == 0) ? null : teamMapper.selectById(pid);
        }
        if (chainIds.isEmpty()) {
            throw new BusinessException("部门不存在");
        }
        List<UserTeamDO> memberships = userTeamMapper.selectList(
            new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getUserId, userId)
                .in(UserTeamDO::getTeamId, chainIds)
        );
        boolean isOwnerInChain = memberships.stream().anyMatch(m -> "OWNER".equals(m.getRoleInTeam()));
        if (!isOwnerInChain) {
            throw new BusinessException("只有部门负责人（含上级部门负责人）才能执行此操作");
        }
    }

    /**
     * 是否拥有"租户管理"页面的超级管理（ADMIN）权限
     */
    private boolean hasTenantManageAdmin(Long userId) {
        try {
            return userService.getUserPagePermissions(userId).stream()
                    .anyMatch(p -> "manage:tenants".equals(p.get("pageCode"))
                            && "ADMIN".equals(p.get("accessLevel")));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 下线部门（递归：整棵子树全部下线；成员归属保留）
     */
    @Transactional
    public void disableWithChildren(Long id) {
        TeamDO team = teamMapper.selectById(id);
        if (team == null) throw new BusinessException("部门不存在");
        // 收集自身+全部后代 id
        List<TeamDO> all = teamMapper.selectList(new LambdaQueryWrapper<TeamDO>().eq(TeamDO::getDeleted, 0));
        Map<Long, List<TeamDO>> childrenMap = new HashMap<>();
        all.forEach(t -> childrenMap.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t));
        List<Long> ids = new ArrayList<>();
        collectDescendants(id, childrenMap, ids);
        ids.add(id);
        for (Long tid : ids) {
            TeamDO t = new TeamDO();
            t.setId(tid);
            t.setStatus(0);
            teamMapper.updateById(t);
        }
        refreshCache();
    }

    private void collectDescendants(Long parentId, Map<Long, List<TeamDO>> childrenMap, List<Long> result) {
        List<TeamDO> children = childrenMap.get(parentId);
        if (children == null) return;
        for (TeamDO child : children) {
            result.add(child.getId());
            collectDescendants(child.getId(), childrenMap, result);
        }
    }

    /**
     * 启用部门（校验：任一上级部门处于已下线状态时拒绝，需先启用上级）
     */
    @Transactional
    public void enableWithParentCheck(Long id) {
        TeamDO team = teamMapper.selectById(id);
        if (team == null) throw new BusinessException("部门不存在");
        // 沿父链向上找第一个已下线的祖先
        java.util.Set<Long> visited = new java.util.HashSet<>();
        TeamDO cur = team.getParentId() != null && team.getParentId() != 0 ? teamMapper.selectById(team.getParentId()) : null;
        while (cur != null && visited.add(cur.getId())) {
            if (cur.getStatus() != null && cur.getStatus() == 0) {
                throw new BusinessException("请先启用上级部门「" + cur.getTeamName() + "」");
            }
            Long pid = cur.getParentId();
            cur = (pid == null || pid == 0) ? null : teamMapper.selectById(pid);
        }
        team.setStatus(1);
        teamMapper.updateById(team);
        refreshCache();
    }

    /**
     * 刷新缓存
     */
    private void refreshCache() {
        treeCache.clear();
        nodeCache.clear();
    }
}