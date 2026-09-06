package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.entity.RagConfigVersionDO;
import com.example.zuoaiagent.raglab.entity.RagExperimentDO;
import com.example.zuoaiagent.raglab.entity.RagPromptTemplateDO;
import com.example.zuoaiagent.raglab.entity.RagPromptVersionDO;
import com.example.zuoaiagent.raglab.entity.RagTestDocumentDO;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import com.example.zuoaiagent.raglab.service.RagConfigService;
import com.example.zuoaiagent.raglab.service.RagEvaluationService;
import com.example.zuoaiagent.raglab.service.RagPromptService;
import com.example.zuoaiagent.raglab.service.RagTestQuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 实验室 API
 *
 * <p>提供流水线配置管理、提示词管理、版本回滚等接口。
 * 所有配置修改后通过 RagConfigLoader 热加载，无需重启服务。
 */
@RestController
@RequestMapping("/rag-lab")
public class RagLabController {

    private final RagConfigService ragConfigService;
    private final RagPromptService ragPromptService;
    private final RagTestQuestionService testQuestionService;
    private final RagEvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public RagLabController(RagConfigService ragConfigService,
                            RagPromptService ragPromptService,
                            RagTestQuestionService testQuestionService,
                            RagEvaluationService evaluationService,
                            ObjectMapper objectMapper) {
        this.ragConfigService = ragConfigService;
        this.ragPromptService = ragPromptService;
        this.testQuestionService = testQuestionService;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
    }

    // ==================== 流水线配置 ====================

    /**
     * 获取当前生效的 RAG 配置。
     */
    @GetMapping("/config")
    public BaseResponse<RagConfigDO> getConfig() {
        return ResultUtils.success(ragConfigService.getActiveConfig());
    }

    /**
     * 获取所有配置列表。
     */
    @GetMapping("/config/list")
    public BaseResponse<List<RagConfigDO>> listConfigs() {
        return ResultUtils.success(ragConfigService.listAll());
    }

    /**
     * 获取指定配置详情。
     */
    @GetMapping("/config/{id}")
    public BaseResponse<RagConfigDO> getConfigById(@PathVariable Long id) {
        return ResultUtils.success(ragConfigService.getById(id));
    }

    /**
     * 更新 RAG 配置（自动创建版本快照 + 热加载）。
     *
     * @param body 包含 config（RagConfigDO JSON）和 changeLog
     */
    @PutMapping("/config")
    public BaseResponse<RagConfigDO> updateConfig(@RequestBody RagConfigUpdateBody body) {
        RagConfigDO config = body.getConfig();
        String changeLog = body.getChangeLog() != null ? body.getChangeLog() : "通过API更新";
        return ResultUtils.success(ragConfigService.updateConfig(config, changeLog));
    }

    /**
     * 切换生效配置。
     */
    @PostMapping("/config/{id}/activate")
    public BaseResponse<String> activateConfig(@PathVariable Long id) {
        ragConfigService.activateConfig(id);
        return ResultUtils.success("配置已切换生效");
    }

    /**
     * 获取配置版本历史。
     */
    @GetMapping("/config/{id}/versions")
    public BaseResponse<List<RagConfigVersionDO>> getConfigVersions(@PathVariable Long id) {
        return ResultUtils.success(ragConfigService.getVersionHistory(id));
    }

    /**
     * 回滚配置到指定版本。
     */
    @PostMapping("/config/{id}/rollback/{versionId}")
    public BaseResponse<RagConfigDO> rollbackConfig(@PathVariable Long id,
                                                     @PathVariable Long versionId) {
        return ResultUtils.success(ragConfigService.rollbackToVersion(id, versionId));
    }

    // ==================== 提示词模板 ====================

    /**
     * 获取所有提示词模板。
     */
    @GetMapping("/prompts")
    public BaseResponse<List<RagPromptTemplateDO>> listPrompts() {
        return ResultUtils.success(ragPromptService.listAll());
    }

    /**
     * 获取指定类型的提示词。
     */
    @GetMapping("/prompts/{type}")
    public BaseResponse<RagPromptTemplateDO> getPrompt(@PathVariable String type) {
        return ResultUtils.success(ragPromptService.getByType(type));
    }

    /**
     * 更新提示词模板（自动创建版本快照）。
     */
    @PutMapping("/prompts/{type}")
    public BaseResponse<RagPromptTemplateDO> updatePrompt(@PathVariable String type,
                                                           @RequestBody PromptUpdateBody body) {
        return ResultUtils.success(
                ragPromptService.updateTemplate(type, body.getTemplateContent(),
                        body.getTemplateName(), body.getChangeLog()));
    }

    /**
     * 获取提示词版本历史。
     */
    @GetMapping("/prompts/{id}/versions")
    public BaseResponse<List<RagPromptVersionDO>> getPromptVersions(@PathVariable Long id) {
        return ResultUtils.success(ragPromptService.getVersionHistory(id));
    }

    /**
     * 回滚提示词到指定版本。
     */
    @PostMapping("/prompts/{id}/rollback/{versionId}")
    public BaseResponse<RagPromptTemplateDO> rollbackPrompt(@PathVariable Long id,
                                                              @PathVariable Long versionId) {
        return ResultUtils.success(ragPromptService.rollbackToVersion(id, versionId));
    }

    // ==================== 测试题库 ====================

    /**
     * 获取所有测试题目。
     */
    @GetMapping("/test-questions")
    public BaseResponse<List<RagTestQuestionDO>> listTestQuestions() {
        return ResultUtils.success(testQuestionService.listQuestions());
    }

    /**
     * 获取指定测试题目。
     */
    @GetMapping("/test-questions/{id}")
    public BaseResponse<RagTestQuestionDO> getTestQuestion(@PathVariable Long id) {
        return ResultUtils.success(testQuestionService.getQuestionById(id));
    }

    /**
     * 创建测试题目。
     */
    @PostMapping("/test-questions")
    public BaseResponse<RagTestQuestionDO> createTestQuestion(@RequestBody RagTestQuestionDO question) {
        return ResultUtils.success(testQuestionService.createQuestion(question));
    }

    /**
     * 更新测试题目。
     */
    @PutMapping("/test-questions/{id}")
    public BaseResponse<RagTestQuestionDO> updateTestQuestion(@PathVariable Long id,
                                                               @RequestBody RagTestQuestionDO question) {
        question.setId(id);
        return ResultUtils.success(testQuestionService.updateQuestion(question));
    }

    /**
     * 删除测试题目。
     */
    @DeleteMapping("/test-questions/{id}")
    public BaseResponse<String> deleteTestQuestion(@PathVariable Long id) {
        testQuestionService.deleteQuestion(id);
        return ResultUtils.success("已删除");
    }

    // ==================== 测试文档 ====================

    /**
     * 获取所有测试文档。
     */
    @GetMapping("/test-documents")
    public BaseResponse<List<RagTestDocumentDO>> listTestDocuments() {
        return ResultUtils.success(testQuestionService.listDocuments());
    }

    /**
     * 创建测试文档。
     */
    @PostMapping("/test-documents")
    public BaseResponse<RagTestDocumentDO> createTestDocument(@RequestBody RagTestDocumentDO document) {
        return ResultUtils.success(testQuestionService.createDocument(document));
    }

    /**
     * 更新测试文档。
     */
    @PutMapping("/test-documents/{id}")
    public BaseResponse<RagTestDocumentDO> updateTestDocument(@PathVariable Long id,
                                                               @RequestBody RagTestDocumentDO document) {
        document.setId(id);
        return ResultUtils.success(testQuestionService.updateDocument(document));
    }

    /**
     * 删除测试文档。
     */
    @DeleteMapping("/test-documents/{id}")
    public BaseResponse<String> deleteTestDocument(@PathVariable Long id) {
        testQuestionService.deleteDocument(id);
        return ResultUtils.success("已删除");
    }

    // ==================== 实验评估 ====================

    /**
     * 获取所有实验列表。
     */
    @GetMapping("/experiments")
    public BaseResponse<List<RagExperimentDO>> listExperiments() {
        return ResultUtils.success(evaluationService.listExperiments());
    }

    /**
     * 获取实验详情。
     */
    @GetMapping("/experiments/{id}")
    public BaseResponse<RagExperimentDO> getExperiment(@PathVariable Long id) {
        return ResultUtils.success(evaluationService.getExperiment(id));
    }

    /**
     * 启动评估实验。
     *
     * @param body { experimentName: string, testQuestionIds: number[] }
     */
    @PostMapping("/experiments/run")
    public BaseResponse<RagExperimentDO> runExperiment(@RequestBody ExperimentRunBody body) {
        return ResultUtils.success(
                evaluationService.startExperiment(body.getExperimentName(), body.getTestQuestionIds()));
    }

    /**
     * 获取实验报告（含明细）。
     */
    @GetMapping("/experiments/{id}/report")
    public BaseResponse<?> getExperimentReport(@PathVariable Long id) {
        RagExperimentDO exp = evaluationService.getExperiment(id);
        if (exp == null) {
            return ResultUtils.error(404, "实验不存在");
        }
        // 构造报告：汇总指标 + 逐题明细
        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("experimentName", exp.getExperimentName());
        report.put("status", exp.getStatus());
        report.put("runDurationMs", exp.getRunDurationMs());
        report.put("intentAccuracy", exp.getIntentAccuracy());
        report.put("rewriteAccuracy", exp.getRewriteAccuracy());
        report.put("hydeRelevance", exp.getHydeRelevance());
        report.put("recallAt3", exp.getRecallAt3());
        report.put("recallAt5", exp.getRecallAt5());
        report.put("recallAt10", exp.getRecallAt10());
        report.put("mrr", exp.getMrr());
        report.put("rerankNdcgAt3", exp.getRerankNdcgAt3());
        report.put("rerankNdcgAt5", exp.getRerankNdcgAt5());
        report.put("answerFaithfulness", exp.getAnswerFaithfulness());
        report.put("answerCompleteness", exp.getAnswerCompleteness());
        report.put("hallucinationRate", exp.getHallucinationRate());

        // 解析明细
        if (exp.getDetailData() != null) {
            try {
                List<Map<String, Object>> details = objectMapper.readValue(
                        exp.getDetailData(), new TypeReference<List<Map<String, Object>>>() {});
                report.put("details", details);
            } catch (Exception e) {
                report.put("details", List.of());
            }
        }
        return ResultUtils.success(report);
    }

    // ==================== 请求体 ====================

    public static class RagConfigUpdateBody {
        private RagConfigDO config;
        private String changeLog;

        public RagConfigDO getConfig() { return config; }
        public void setConfig(RagConfigDO config) { this.config = config; }
        public String getChangeLog() { return changeLog; }
        public void setChangeLog(String changeLog) { this.changeLog = changeLog; }
    }

    public static class PromptUpdateBody {
        private String templateContent;
        private String templateName;
        private String changeLog;

        public String getTemplateContent() { return templateContent; }
        public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getChangeLog() { return changeLog; }
        public void setChangeLog(String changeLog) { this.changeLog = changeLog; }
    }

    public static class ExperimentRunBody {
        private String experimentName;
        private List<Long> testQuestionIds;

        public String getExperimentName() { return experimentName; }
        public void setExperimentName(String experimentName) { this.experimentName = experimentName; }
        public List<Long> getTestQuestionIds() { return testQuestionIds; }
        public void setTestQuestionIds(List<Long> testQuestionIds) { this.testQuestionIds = testQuestionIds; }
    }
}
