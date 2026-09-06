package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM 模型配置控制器
 */
@RestController
@RequestMapping("/rag-lab/model-config")
@Tag(name = "LLM 模型配置", description = "LLM 模型配置管理 API")
public class LlmModelConfigController {

    private final LlmModelConfigService configService;

    public LlmModelConfigController(LlmModelConfigService configService) {
        this.configService = configService;
    }

    @PostMapping
    @Operation(summary = "创建模型配置")
    public LlmModelConfigDO create(@RequestBody LlmModelConfigDO config) {
        return configService.create(config);
    }

    @PutMapping
    @Operation(summary = "更新模型配置")
    public LlmModelConfigDO update(@RequestBody LlmModelConfigDO config) {
        return configService.update(config);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模型配置")
    public LlmModelConfigDO getById(@PathVariable Long id) {
        return configService.getById(id);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有模型配置")
    public List<LlmModelConfigDO> listAll() {
        return configService.listAll();
    }

    @GetMapping("/list/active")
    @Operation(summary = "查询对话模块可选模型（is_active=1）")
    public List<LlmModelConfigDO> listActive() {
        return configService.listActive();
    }

    @PostMapping("/{id}/toggle-active")
    @Operation(summary = "切换激活状态")
    public void toggleActive(@PathVariable Long id) {
        configService.toggleActive(id);
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试连通性")
    public boolean testConnectivity(@PathVariable Long id) {
        return configService.testConnectivity(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public void delete(@PathVariable Long id) {
        configService.delete(id);
    }
}
