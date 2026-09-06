package com.example.zuoaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TokenBudgetTrimmer {

    private static final int DEFAULT_MAX_TOKENS = 3000;
    private static final double DEFAULT_TOKEN_COEFFICIENT = 0.4;

    public List<Document> trim(List<Document> documents, int maxTokens) {
        return trim(documents, maxTokens, DEFAULT_TOKEN_COEFFICIENT);
    }

    public List<Document> trim(List<Document> documents, int maxTokens, double tokenCoefficient) {
        if (documents == null || documents.isEmpty()) return List.of();
        if (maxTokens <= 0) maxTokens = DEFAULT_MAX_TOKENS;
        if (tokenCoefficient <= 0) tokenCoefficient = DEFAULT_TOKEN_COEFFICIENT;

        List<Document> result = new ArrayList<>();
        int totalTokens = 0;
        for (Document doc : documents) {
            int docTokens = estimateTokens(doc.getFormattedContent(), tokenCoefficient);
            if (totalTokens + docTokens > maxTokens) break;
            result.add(doc);
            totalTokens += docTokens;
        }
        return result;
    }

    public List<Document> trim(List<Document> documents) {
        return trim(documents, DEFAULT_MAX_TOKENS);
    }

    private int estimateTokens(String text, double coefficient) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() * coefficient);
    }
}