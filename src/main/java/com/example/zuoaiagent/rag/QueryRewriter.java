package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private static final String TEMPLATE_PATH = "prompts/query-rewrite.st";

    private final RoutingChatService routingChatService;
    private final PromptTemplateLoader templateLoader;

    public QueryRewriter(RoutingChatService routingChatService,
                         PromptTemplateLoader templateLoader) {
        this.routingChatService = routingChatService;
        this.templateLoader = templateLoader;
    }

    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        try {
            String promptText = templateLoader.render(TEMPLATE_PATH, Map.of("query", query));
            String rewritten = routingChatService.chat(promptText, null, null, null);
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