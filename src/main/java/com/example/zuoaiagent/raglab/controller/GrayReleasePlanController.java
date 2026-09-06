package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.raglab.entity.GrayReleasePlanDO;
import com.example.zuoaiagent.raglab.service.GrayReleasePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 灰度发布计划控制器
 */
@RestController
@RequestMapping("/rag-lab/gray-release")
@Tag(name = "灰度发布计划", description = "灰度发布计划管理 API")
public class GrayReleasePlanController {

    private final GrayReleasePlanService grayReleasePlanService;

    public GrayReleasePlanController(GrayReleasePlanService grayReleasePlanService) {
        this.grayReleasePlanService = grayReleasePlanService;
    }

    @PostMapping
    @Operation(summary = "创建发布计划")
    public GrayReleasePlanDO createPlan(@RequestBody GrayReleasePlanDO plan) {
        return grayReleasePlanService.createPlan(plan);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询发布计划")
    public GrayReleasePlanDO getPlan(@PathVariable Long id) {
        return grayReleasePlanService.getById(id);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有发布计划")
    public List<GrayReleasePlanDO> listPlans() {
        return grayReleasePlanService.listPlans();
    }

    @GetMapping("/list/{componentType}")
    @Operation(summary = "按组件类型查询发布计划")
    public List<GrayReleasePlanDO> listByComponent(@PathVariable String componentType) {
        return grayReleasePlanService.listByComponent(componentType);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过")
    public GrayReleasePlanDO approve(@PathVariable Long id, @RequestParam Long approvedBy) {
        return grayReleasePlanService.approve(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "审批拒绝")
    public GrayReleasePlanDO reject(@PathVariable Long id, @RequestParam Long approvedBy) {
        return grayReleasePlanService.reject(id, approvedBy);
    }

    @PostMapping("/{id}/start-gray")
    @Operation(summary = "开始灰度")
    public GrayReleasePlanDO startGray(@PathVariable Long id) {
        return grayReleasePlanService.startGray(id);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "全量发布")
    public GrayReleasePlanDO activate(@PathVariable Long id) {
        return grayReleasePlanService.activate(id);
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚")
    public GrayReleasePlanDO rollback(@PathVariable Long id) {
        return grayReleasePlanService.rollback(id);
    }

    @GetMapping("/{id}/active")
    @Operation(summary = "查询组件当前灰度中的发布计划")
    public GrayReleasePlanDO findActivePlan(
            @PathVariable Long id,
            @RequestParam String componentType) {
        return grayReleasePlanService.findActivePlan(componentType, id);
    }

    @GetMapping("/{id}/user-in-gray")
    @Operation(summary = "判断用户是否命中灰度")
    public boolean isUserInGray(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam String componentType) {
        return grayReleasePlanService.isUserInGray(userId, componentType, id);
    }

    @PostMapping("/{id}/metrics")
    @Operation(summary = "更新灰度指标快照")
    public void updateMetrics(@PathVariable Long id, @RequestParam String metricsJson) {
        grayReleasePlanService.updateMetricsSnapshot(id, metricsJson);
    }
}
