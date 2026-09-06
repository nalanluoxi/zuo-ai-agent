package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.log.LogTransaction;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class DocumentReranker {

    private static final Logger log = LoggerFactory.getLogger(DocumentReranker.class);

    private static final int DEFAULT_TOP_K = 3;
    private static final double CONFIDENCE_THRESHOLD = 0.5;
    private static final String TEMPLATE_PATH = "prompts/document-score.st";

    private final RoutingChatService routingChatService;
    private final PromptTemplateLoader templateLoader;

    public DocumentReranker(RoutingChatService routingChatService,
                             PromptTemplateLoader templateLoader) {
        this.routingChatService = routingChatService;
        this.templateLoader = templateLoader;
    }

    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        if (topK <= 0) {
            topK = DEFAULT_TOP_K;
        }

        List<ScoredDocument> scored = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            double score = scoreDocument(query, doc);
            scored.add(new ScoredDocument(doc, score));
        }

        int finalTopK = Math.min(topK, scored.size());
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(finalTopK)
                .map(ScoredDocument::document)
                .toList();
    }

    public List<Document> rerank(String query, List<Document> documents) {
        return rerank(query, documents, DEFAULT_TOP_K);
    }

    public List<Document> rerank(String query, List<Document> documents, int topK, double confidenceThreshold) {
        if (documents == null || documents.isEmpty()) return List.of();
        if (topK <= 0) topK = DEFAULT_TOP_K;

        List<ScoredDocument> scored = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            double score = scoreDocument(query, doc);
            if (score >= confidenceThreshold) {
                scored.add(new ScoredDocument(doc, score));
            }
        }
        if (scored.isEmpty()) return List.of();

        int finalTopK = Math.min(topK, scored.size());
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(finalTopK)
                .map(ScoredDocument::document)
                .toList();
    }

    @LogTransaction(name = "LLM重排序", eventType = "RERANK", logOutput = false, maxOutputLength = 200)
    public List<Document> rerank(String query, List<Document> documents, int topK,
                                  double confidenceThreshold, int docTruncate) {
        if (documents == null || documents.isEmpty()) return List.of();
        if (topK <= 0) topK = DEFAULT_TOP_K;
        if (docTruncate <= 0) docTruncate = 800;

        List<ScoredDocument> scored = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            double score = scoreDocument(query, doc, docTruncate);
            if (score >= confidenceThreshold) {
                scored.add(new ScoredDocument(doc, score));
            }
        }
        if (scored.isEmpty()) return List.of();

        int finalTopK = Math.min(topK, scored.size());
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(finalTopK)
                .map(ScoredDocument::document)
                .toList();
    }

    private double scoreDocument(String query, Document doc) {
        return scoreDocument(query, doc, 800);
    }

    private double scoreDocument(String query, Document doc, int truncateLength) {
        String content = doc.getFormattedContent();
        if (content.length() > truncateLength) {
            content = content.substring(0, truncateLength) + "...";
        }
        try {
            String promptText = templateLoader.render(TEMPLATE_PATH, Map.of("query", query, "content", content));
            String response = routingChatService.chat(promptText, null, null, null);
            if (response != null) {
                String trimmed = response.strip();
                return Double.parseDouble(trimmed);
            }
        } catch (NumberFormatException e) {
            log.debug("[DocumentReranker] 分数解析失败，使用默认分 0");
        } catch (Exception e) {
            log.warn("[DocumentReranker] 文档评分失败，使用默认分 0。原因: {}", e.getMessage());
        }
        return 0.0;
    }

    private record ScoredDocument(Document document, double score) {}
}