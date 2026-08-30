package com.example.zuoaiagent.intent.service;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.intent.entity.IntentNodeDO;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private static final double HIGH_CONFIDENCE = 0.85;
    private static final String TEMPLATE_PATH = "prompts/intent-classify.st";

    private final RoutingChatService routingChatService;
    private final IntentTreeService intentTreeService;
    private final PromptTemplateLoader templateLoader;

    public IntentClassifier(RoutingChatService routingChatService,
                            IntentTreeService intentTreeService,
                            PromptTemplateLoader templateLoader) {
        this.routingChatService = routingChatService;
        this.intentTreeService = intentTreeService;
        this.templateLoader = templateLoader;
    }

    public IntentResult classify(String query) {
        if (query == null || query.isBlank()) {
            return IntentResult.unknown();
        }

        String treeText = intentTreeService.buildTreeText();
        if (treeText.isBlank() || treeText.equals("（意图树为空）")) {
            log.warn("[IntentClassifier] 意图树为空，降级为 unknown");
            return IntentResult.unknown();
        }

        try {
            String promptText = templateLoader.render(TEMPLATE_PATH, Map.of(
                    "intentTree", treeText,
                    "query", query
            ));
            String response = routingChatService.chat(promptText, null, null, null);
            if (response == null || response.isBlank()) {
                return IntentResult.unknown();
            }

            String trimmed = response.strip();
            long nodeId = Long.parseLong(trimmed);

            if (nodeId == -1L) {
                log.debug("[IntentClassifier] 分类为闲聊, query={}", query);
                return IntentResult.system(-1L, "闲聊/通用");
            }

            IntentNodeDO node = intentTreeService.getNode(nodeId);
            if (node == null) {
                log.warn("[IntentClassifier] LLM 返回了未知节点 ID={}, 降级为 unknown", nodeId);
                return IntentResult.unknown();
            }

            boolean isSystem = node.getIsSystem() != null && node.getIsSystem() == 1;
            if (isSystem) {
                return IntentResult.system(nodeId, node.getLabel());
            }

            IntentResult result = new IntentResult(nodeId, node.getLabel(), HIGH_CONFIDENCE, false, node.getKbId());
            log.debug("[IntentClassifier] 分类结果: {}, query={}", result, query);
            return result;

        } catch (NumberFormatException e) {
            log.warn("[IntentClassifier] LLM 返回非整数，降级为 unknown。query={}", query);
            return IntentResult.unknown();
        } catch (Exception e) {
            log.warn("[IntentClassifier] 分类异常，降级为 unknown。原因: {}", e.getMessage());
            return IntentResult.unknown();
        }
    }
}