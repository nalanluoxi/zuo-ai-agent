package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.chat.ChatModelFactory;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.config.TenantContextHolder;
import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.service.GrayReleasePlanService;
import com.example.zuoaiagent.raglab.service.LlmModelConfigService;
import com.example.zuoaiagent.raglab.service.impl.ModelRouterServiceImpl;
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
    private final ModelRouterServiceImpl modelRouterService;
    private final GrayReleasePlanService grayReleasePlanService;
    private final ChatModelFactory chatModelFactory;

    public LlmModelConfigController(LlmModelConfigService configService,
                                    ModelRouterServiceImpl modelRouterService,
                                    GrayReleasePlanService grayReleasePlanService,
                                    ChatModelFactory chatModelFactory) {
        this.configService = configService;
        this.modelRouterService = modelRouterService;
        this.grayReleasePlanService = grayReleasePlanService;
        this.chatModelFactory = chatModelFactory;
    }

    @PostMapping
    @Operation(summary = "创建模型配置")
    public LlmModelConfigDO create(@RequestBody LlmModelConfigDO config) {
        LlmModelConfigDO created = configService.create(config);
        // 重新加载模型列表
        chatModelFactory.reload();
        return created;
    }

    @PutMapping
    @Operation(summary = "更新模型配置")
    public LlmModelConfigDO update(@RequestBody LlmModelConfigDO config) {
        LlmModelConfigDO updated = configService.update(config);
        // 清除缓存，使新配置生效
        modelRouterService.evictCache(config.getId());
        // 重新加载模型列表
        chatModelFactory.reload();
        return updated;
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
    public BaseResponse<List<LlmModelConfigDO>> listActive() {
        return ResultUtils.success(configService.listActive());
    }

    @GetMapping("/list/active/gray")
    @Operation(summary = "查询当前用户可见的模型（根据灰度规则过滤）")
    public BaseResponse<List<LlmModelConfigDO>> listActiveGray() {
        Long userId = TenantContextHolder.getUserId();
        List<LlmModelConfigDO> allActive = configService.listActive();
        List<LlmModelConfigDO> visible = grayReleasePlanService.resolveVisibleModels(userId, allActive);
        return ResultUtils.success(visible);
    }

    @PostMapping("/{id}/toggle-active")
    @Operation(summary = "切换激活状态")
    public void toggleActive(@PathVariable Long id) {
        configService.toggleActive(id);
        // 重新加载模型列表
        chatModelFactory.reload();
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
        // 重新加载模型列表
        chatModelFactory.reload();
    }

    @PostMapping("/reload")
    @Operation(summary = "手动触发模型重载")
    public BaseResponse<String> reload() {
        // 先清除缓存
        modelRouterService.evictAllCache();
        // 再重新加载模型列表
        chatModelFactory.reload();
        return ResultUtils.success("模型列表已重新加载");
    }
}
