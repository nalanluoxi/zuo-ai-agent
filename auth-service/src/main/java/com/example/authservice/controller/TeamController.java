package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.authservice.entity.TeamDO;
import com.example.authservice.entity.UserDO;
import com.example.authservice.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;

    @GetMapping("/my")
    @SaCheckLogin
    public List<TeamDO> myTeams() { return teamService.listMyTeams(); }

    @GetMapping("/all")
    @SaCheckLogin
    public List<TeamDO> allTeams() { return teamService.listAll(); }

    /**
     * P7 修复：获取团队树形结构
     */
    @GetMapping("/tree")
    @SaCheckLogin
    public List<TeamDO> getTeamTree(@RequestParam Long tenantId) {
        return teamService.getTeamTree(tenantId);
    }

    @PostMapping
    @SaCheckLogin
    public TeamDO create(@RequestBody Map<String, Object> body) {
        return teamService.create(body.get("name").toString(),
                Long.valueOf(body.get("tenantId").toString()));
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    public TeamDO getById(@PathVariable Long id) { return teamService.getById(id); }

    @GetMapping("/{id}/members")
    @SaCheckLogin
    public List<UserDO> members(@PathVariable Long id) { return teamService.getMembers(id); }

    /**
     * P8 修复：添加团队成员（带权限检查）
     */
    @PostMapping("/{id}/members")
    @SaCheckLogin
    public void addMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        teamService.addMember(id,
                Long.valueOf(body.get("userId").toString()),
                (String) body.get("roleInTeam"));
    }

    /**
     * P10 修复：移除团队成员（带权限检查）
     */
    @DeleteMapping("/{id}/members/{userId}")
    @SaCheckLogin
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMember(id, userId);
    }

    /**
     * P7 修复：添加子部门
     */
    @PostMapping("/{parentId}/subteam")
    @SaCheckLogin
    public TeamDO addSubTeam(
            @PathVariable Long parentId,
            @RequestBody Map<String, Object> body) {
        return teamService.addSubTeam(
                parentId,
                body.get("subTeamName").toString(),
                Long.valueOf(body.get("tenantId").toString())
        );
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    public void update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        TeamDO team = teamService.getById(id);
        if (team == null) throw new RuntimeException("团队不存在");
        if (body.containsKey("name")) team.setTeamName(body.get("name").toString());
        if (body.containsKey("parentId")) team.setParentId(body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null);
        teamService.update(team);
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public void delete(@PathVariable Long id) { teamService.delete(id); }

    @PutMapping("/{id}/enable")
    @SaCheckLogin
    public void enable(@PathVariable Long id) { TeamDO t = teamService.getById(id); t.setStatus(1); teamService.update(t); }

    @PutMapping("/{id}/disable")
    @SaCheckLogin
    public void disable(@PathVariable Long id) { TeamDO t = teamService.getById(id); t.setStatus(0); teamService.update(t); }

    /**
     * P9 修复：设置团队负责人（部门负责人）
     */
    @PutMapping("/{id}/leader")
    @SaCheckLogin
    public void setLeader(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long leaderId = Long.valueOf(body.get("leaderId").toString());
        teamService.setTeamLeader(id, leaderId);
    }
}
