package com.example.authservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.authservice.entity.ApprovalDO;
import com.example.authservice.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalService approvalService;

    @PostMapping("/submit")
    @SaCheckLogin
    public ApprovalDO submit(@RequestBody Map<String, Object> body) {
        return approvalService.submit(
                body.get("applyType").toString(),
                body.get("targetType").toString(),
                Long.valueOf(body.get("targetId").toString()),
                body.get("targetName").toString(),
                body.get("approverType").toString(),
                Long.valueOf(body.get("approverId").toString()));
    }

    @GetMapping("/pending")
    @SaCheckLogin
    public Page<ApprovalDO> pending(@RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "10") int size) {
        return approvalService.pendingList(StpUtil.getLoginIdAsLong(), current, size);
    }

    @GetMapping("/my")
    @SaCheckLogin
    public Page<ApprovalDO> myList(@RequestParam(defaultValue = "1") int current,
                                    @RequestParam(defaultValue = "10") int size) {
        return approvalService.myList(current, size);
    }

    @PostMapping("/{id}/approve")
    @SaCheckLogin
    public void approve(@PathVariable Long id) { approvalService.approve(id, null); }

    @PostMapping("/{id}/reject")
    @SaCheckLogin
    public void reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        approvalService.approve(id, body.get("reason"));
    }
}