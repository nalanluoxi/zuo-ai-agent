package com.example.zuoaiagent.raglab.service;

import org.springframework.ai.chat.model.ChatModel;

/**
 * 模型路由器服务
 * 根据配置动态创建 ChatModel 实例（统一 OpenAI 协议）
 */
public interface ModelRouterService {

    /**
     * 根据模型配置 ID 动态创建 ChatModel
     * @param modelConfigId 模型配置 ID
     * @return ChatModel 实例
     */
    ChatModel resolveChatModel(Long modelConfigId);

    /**
     * 根据模型配置 ID 获取模型名称
     */
    String getModelName(Long modelConfigId);
}
