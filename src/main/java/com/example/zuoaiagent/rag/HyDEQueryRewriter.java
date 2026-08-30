package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HyDEQueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(HyDEQueryRewriter.class);

    private final RoutingChatService routingChatService;
    private final PromptTemplateLoader templateLoader;

    private volatile boolean enabled = false;
    private volatile boolean experimentMode = false;
    private volatile double experimentRatio = 0.5;

    public HyDEQueryRewriter(RoutingChatService routingChatService,
                              PromptTemplateLoader templateLoader) {
        this.routingChatService = routingChatService;
        this.templateLoader = templateLoader;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isExperimentMode() { return experimentMode; }
    public void setExperimentMode(boolean experimentMode) { this.experimentMode = experimentMode; }
    public double getExperimentRatio() { return experimentRatio; }
    public void setExperimentRatio(double experimentRatio) { this.experimentRatio = experimentRatio; }

    public boolean shouldUseHyDE() {
        if (enabled) return true;
        if (experimentMode) return Math.random() < experimentRatio;
        return false;
    }

    public String generateHypothesisDocument(String query) {
        try {
            String prompt = "请根据以下问题，假设你是一个知识库，生成一段可能包含答案的文档内容（不超过200字）：\n\n" + query;
            String result = routingChatService.chat(prompt, null, null, null);
            return result != null ? result.strip() : "";
        } catch (Exception e) {
            log.warn("[HyDEQueryRewriter] 假设文档生成失败: {}", e.getMessage());
            return "";
        }
    }

    public List<String> generateEquivalentQueries(String query, int count) {
        List<String> queries = new ArrayList<>();
        try {
            String prompt = "请将以下问题改写为" + count + "个不同表述方式的等价查询，每行一个：\n\n" + query;
            String result = routingChatService.chat(prompt, null, null, null);
            if (result != null) {
                for (String line : result.split("\n")) {
                    String trimmed = line.replaceAll("^\\d+\\.?\\s*", "").strip();
                    if (!trimmed.isBlank()) queries.add(trimmed);
                }
            }
        } catch (Exception e) {
            log.warn("[HyDEQueryRewriter] 等价查询生成失败: {}", e.getMessage());
        }
        return queries;
    }
}