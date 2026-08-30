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
    public TeamDO create(String name, Long tenantId) {
        TeamDO team = new TeamDO();
        team.setTeamName(name); team.setTenantId(tenantId);
        team.setOwnerId(StpUtil.getLoginIdAsLong()); team.setStatus(1);
        teamMapper.insert(team);
        UserTeamDO ut = new UserTeamDO();
        ut.setUserId(StpUtil.getLoginIdAsLong()); ut.setTeamId(team.getId()); ut.setRoleInTeam("OWNER");
        userTeamMapper.insert(ut);
        refreshCache();
        return team;
    }

    public TeamDO getById(Long id) { return teamMapper.selectById(id); }

    public List<UserDO> getMembers(Long teamId) {
        List<Long> userIds = userTeamMapper.selectList(new LambdaQueryWrapper<UserTeamDO>().eq(UserTeamDO::getTeamId, teamId))
                .stream().map(UserTeamDO::getUserId).toList();
        return userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
    }

    @Transactional
    public void addMember(Long teamId, Long userId, String roleInTeam) {
        // P8 修复：权限校验 - 只有 OWNER 和 ADMIN 才能添加成员
        Long currentUserId = StpUtil.getLoginIdAsLong();
        validateTeamPermission(teamId, currentUserId, "OWNER");
        
        if (userTeamMapper.selectOne(new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, teamId).eq(UserTeamDO::getUserId, userId)) != null) return;
        UserTeamDO ut = new UserTeamDO();
        ut.setTeamId(teamId); ut.setUserId(userId); ut.setRoleInTeam(roleInTeam != null ? roleInTeam : "MEMBER");
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

    public List<TeamDO> listAll() { return teamMapper.selectList(new LambdaQueryWrapper<>()); }

    public void update(TeamDO team) { 
        teamMapper.updateById(team);
        refreshCache();
    }

    public void delete(Long id) { 
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
     * 递归构建树结构（使用虚拟字段存储子节点）
     */
    private void buildTeamTree(TeamDO parent, Map<Long, List<TeamDO>> childrenMap) {
        List<TeamDO> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            for (TeamDO child : children) {
                buildTeamTree(child, childrenMap);
            }
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
        UserTeamDO userTeam = userTeamMapper.selectOne(
            new LambdaQueryWrapper<UserTeamDO>()
                .eq(UserTeamDO::getTeamId, teamId)
                .eq(UserTeamDO::getUserId, userId)
        );
        
        if (userTeam == null) {
            throw new BusinessException("你不是该团队成员，无法执行此操作");
        }
        
        // 检查角色权限
        String userRole = userTeam.getRoleInTeam();
        if (requiredRole.equals("OWNER") && !userRole.equals("OWNER")) {
            throw new BusinessException("只有团队负责人才能执行此操作");
        }
    }

    /**
     * 刷新缓存
     */
    private void refreshCache() {
        treeCache.clear();
        nodeCache.clear();
    }
}