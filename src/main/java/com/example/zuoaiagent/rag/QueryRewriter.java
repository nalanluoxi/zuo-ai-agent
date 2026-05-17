package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 查询改写器
 *
 * <p>在 RAG 检索前将用户原始问题改写为更适合向量检索的独立语义查询，
 * 解决对话上下文中指代不明（"它是什么"、"上面说的那个"）等导致检索效果差的问题。
 * Prompt 模板从 classpath:prompts/query-rewrite.st 加载。
 */
@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private static final String TEMPLATE_PATH = "prompts/query-rewrite.st";

    private final ChatModel chatModel;
    private final PromptTemplateLoader templateLoader;

    public QueryRewriter(@Qualifier("dashscopeChatModel") ChatModel chatModel,
                         PromptTemplateLoader templateLoader) {
        this.chatModel = chatModel;
        this.templateLoader = templateLoader;
    }

    /**
     * 改写查询语句。
     *
     * @param query 用户原始问题
     * @return 改写后的查询语句；改写失败时返回原始 query
     */
    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        try {
            String promptText = templateLoader.render(TEMPLATE_PATH, Map.of("query", query));
            String rewritten = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
            if (rewritten != null && !rewritten.isBlank()) {
                log.debug("[QueryRewriter] 原始: {} → 改写: {}", query, rewritten.strip());
                return rewritten.strip();
            }
        } catch (Exception e) {
            log.warn("[QueryRewriter] 查询改写失败，降级使用原始 query。原因: {}", e.getMessage());
        }
        return query;
    }
}
