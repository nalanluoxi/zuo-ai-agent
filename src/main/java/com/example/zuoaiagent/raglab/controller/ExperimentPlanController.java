package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagPlanCustomQuestionDO;
import com.example.zuoaiagent.raglab.service.ExperimentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实验计划控制器
 */
@RestController
@RequestMapping("/rag-lab/experiment-plan")
@Tag(name = "实验计划", description = "实验计划管理 API")
public class ExperimentPlanController {

    private final ExperimentPlanService planService;

    public ExperimentPlanController(ExperimentPlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @Operation(summary = "创建实验计划")
    public BaseResponse<RagExperimentPlanDO> createPlan(@RequestBody Map<String, Object> body) {
        RagExperimentPlanDO plan = new RagExperimentPlanDO();
        plan.setPlanName((String) body.get("planName"));
        plan.setModelConfigId(body.get("modelConfigId") != null ? Long.valueOf(body.get("modelConfigId").toString()) : null);
        plan.setConfigVersionId(body.get("configVersionId") != null ? Long.valueOf(body.get("configVersionId").toString()) : null);
        plan.setPromptVersionId(body.get("promptVersionId") != null ? Long.valueOf(body.get("promptVersionId").toString()) : null);
        plan.setUseGlobalQuestions((Boolean) body.getOrDefault("useGlobalQuestions", true));
        plan.setUseCustomQuestions((Boolean) body.getOrDefault("useCustomQuestions", false));

        // 处理知识库 ID 列表
        Object knowledgeBaseIdsObj = body.get("knowledgeBaseIds");
        if (knowledgeBaseIdsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> kbIds = (List<Object>) knowledgeBaseIdsObj;
            String kbIdsStr = kbIds.stream()
                    .map(obj -> obj.toString())
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            plan.setKnowledgeBaseIds(kbIdsStr);
        } else if (knowledgeBaseIdsObj instanceof String) {
            plan.setKnowledgeBaseIds((String) knowledgeBaseIdsObj);
        }

        @SuppressWarnings("unchecked")
        List<Long> questionIds = body.get("questionIds") != null
                ? ((List<Object>) body.get("questionIds")).stream()
                        .map(obj -> Long.valueOf(obj.toString()))
                        .toList()
                : List.of();

        @SuppressWarnings("unchecked")
        List<RagPlanCustomQuestionDO> customQuestions = body.get("customQuestions") != null
                ? ((List<Map<String, Object>>) body.get("customQuestions")).stream().map(m -> {
                    RagPlanCustomQuestionDO cq = new RagPlanCustomQuestionDO();
                    cq.setQuestionText((String) m.get("questionText"));
                    cq.setExpectedAnswer((String) m.get("expectedAnswer"));
                    cq.setExpectedDocIds((String) m.get("expectedDocIds"));
                    return cq;
                }).toList()
                : List.of();

        return ResultUtils.success(planService.createPlan(plan, questionIds, customQuestions));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询实验计划")
    public BaseResponse<RagExperimentPlanDO> getById(@PathVariable Long id) {
        return ResultUtils.success(planService.getById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有实验计划")
    public BaseResponse<List<RagExperimentPlanDO>> listAll() {
        return ResultUtils.success(planService.listAll());
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行实验计划")
    public BaseResponse<RagExperimentPlanDO> executePlan(@PathVariable Long id) {
        return ResultUtils.success(planService.executePlan(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消执行中的实验计划")
    public BaseResponse<RagExperimentPlanDO> cancelPlan(@PathVariable Long id) {
        return ResultUtils.success(planService.cancelPlan(id));
    }

    @GetMapping("/{id}/questions")
    @Operation(summary = "获取计划关联的全局题库 ID")
    public BaseResponse<List<Long>> getPlanQuestionIds(@PathVariable Long id) {
        return ResultUtils.success(planService.getPlanQuestionIds(id));
    }

    @GetMapping("/{id}/custom-questions")
    @Operation(summary = "获取计划关联的自定义提问")
    public BaseResponse<List<RagPlanCustomQuestionDO>> getPlanCustomQuestions(@PathVariable Long id) {
        return ResultUtils.success(planService.getPlanCustomQuestions(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除实验计划")
    public BaseResponse<String> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ResultUtils.success("已删除");
    }
}
