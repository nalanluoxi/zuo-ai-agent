package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;

import java.util.List;

/**
 * LLM 模型配置服务
 */
public interface LlmModelConfigService {

    LlmModelConfigDO create(LlmModelConfigDO config);

    LlmModelConfigDO update(LlmModelConfigDO config);

    LlmModelConfigDO getById(Long id);

    List<LlmModelConfigDO> listAll();

    /** 查询对话模块可选的模型（is_active=1） */
    List<LlmModelConfigDO> listActive();

    /** 切换激活状态 */
    void toggleActive(Long id);

    /** 测试连通性（发送一条简单消息验证配置是否正确） */
    boolean testConnectivity(Long id);

    void delete(Long id);

    /**
     * 根据 modelId 解密 API Key
     * @return 解密后的 API Key
     */
    String getDecryptedApiKey(Long id);
}
