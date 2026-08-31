package com.example.zuoaiagent.service;

import com.example.zuoaiagent.model.Student;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    String chat(String prompt, String conId);

    Student chat2(String prompt, String conId);

    String chatWithRag(String prompt, String conId, String name);

    void streamChat(String prompt, String conversationId, SseEmitter emitter);

    void streamChatWithRag(String prompt, String conversationId, String name, SseEmitter emitter);

    /**
     * 带完整 RAG 流水线的流式对话：问题改写 → 向量检索 → 重排序 → SSE 流式推送。
     *
     * @param prompt         用户原始问题
     * @param conversationId 会话 ID
     * @param name           AI 助手名称
     * @param enableRewrite  是否启用问题改写
     * @param enableRerank   是否启用重排序
     * @param emitter        SSE 输出通道
     */
    void streamChatWithRagPipeline(String prompt, String conversationId, String name,
                                   boolean enableRewrite, boolean enableRerank, SseEmitter emitter);

    /**
     * 智能 RAG 流水线：意图检测 + 闲聊短路 + 多通道并行检索 + 重排序 + SSE 流式推送。
     * 领域由意图分类结果自动填充，unknown 或闲聊时默认"各领域"。
     *
     * @param prompt         用户原始问题
     * @param conversationId 会话 ID
     * @param name           AI 助手名称
     * @param enableRewrite  是否启用问题改写
     * @param enableRerank   是否启用重排序
     * @param emitter        SSE 输出通道
     */
    void streamChatSmart(String prompt, String conversationId, String name,
                         boolean enableRewrite, boolean enableRerank,
                         boolean enableMemory, Long userId, boolean ragEnabled, SseEmitter emitter);

    String ask(String prompt, String conversationId);
}
