package com.example.zuoaiagent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG Prompt 组装服务
 *
 * <p>根据场景（知识库问答 / 闲聊）选择对应的 .st 模板，
 * 填充变量并拼装最终的系统提示词。
 */
@Service
public class RAGPromptService {

    private static final Logger log = LoggerFactory.getLogger(RAGPromptService.class);

    static final String RAG_KB_PROMPT_PATH     = "prompts/rag-kb.st";
    static final String SYSTEM_CHAT_PROMPT_PATH = "prompts/system-chat.st";

    private final PromptTemplateLoader templateLoader;
    private final ContextFormatter contextFormatter;

    public RAGPromptService(PromptTemplateLoader templateLoader, ContextFormatter contextFormatter) {
        this.templateLoader = templateLoader;
        this.contextFormatter = contextFormatter;
    }

    /**
     * 构建知识库问答场景的完整系统提示词。
     *
     * <p>格式：系统角色定义模板 + 知识库上下文块
     *
     * @param name   AI 助手名称
     * @param domain 领域名称（由意图分类结果填充）
     * @param docs   重排序后的文档列表
     * @return 最终系统提示词
     */
    public String buildKbPrompt(String name, String domain, List<Document> docs) {
        String basePrompt = templateLoader.render(RAG_KB_PROMPT_PATH, Map.of(
                "name", name,
                "domain", domain
        ));

        String kbContext = contextFormatter.format(docs);

        if (kbContext.isBlank()) {
            log.warn("[RAGPromptService] 知识库上下文为空，降级为通用提示词");
            return buildSystemChatPrompt(name, domain);
        }

        String fullPrompt = basePrompt.trim() + "\n\n" + kbContext;
        log.debug("[RAGPromptService] KB Prompt 构建完成，长度={}", fullPrompt.length());
        return fullPrompt;
    }

    /**
     * 构建闲聊/系统问题场景的系统提示词（不含知识库上下文）。
     *
     * @param name   AI 助手名称
     * @param domain 领域名称
     * @return 系统提示词
     */
    public String buildSystemChatPrompt(String name, String domain) {
        return templateLoader.render(SYSTEM_CHAT_PROMPT_PATH, Map.of(
                "name", name,
                "domain", domain
        ));
    }

    /**
     * 根据场景自动选择并构建系统提示词。
     *
     * @param scene  Prompt 场景
     * @param name   AI 助手名称
     * @param domain 领域名称
     * @param docs   文档列表（KB_ONLY 场景使用）
     * @return 构建完成的系统提示词
     */
    public String build(PromptScene scene, String name, String domain, List<Document> docs) {
        return switch (scene) {
            case KB_ONLY -> buildKbPrompt(name, domain, docs);
            case SYSTEM_CHAT, EMPTY_RETRIEVAL -> buildSystemChatPrompt(name, domain);
        };
    }
}
