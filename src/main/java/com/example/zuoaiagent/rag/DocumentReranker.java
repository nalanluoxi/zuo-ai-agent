package com.example.zuoaiagent.rag;

import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 文档重排序器（LLM-based Reranker）
 *
 * <p>向量检索召回的文档按余弦相似度排序，但语义相关性未必完全对齐。
 * 重排序器使用 LLM 对每个文档片段与问题的相关性打分（0~10），
 * 按分数降序重新排列后取 Top-K，提升最终上下文质量。
 *
 * <p>设计决策：
 * <ul>
 *   <li>单次逐文档评分（串行），避免 prompt 过长；文档较多时有延迟，但效果稳定</li>
 *   <li>评分失败的文档赋予默认分 0，排在末尾</li>
 *   <li>topK 可在调用处指定，默认 3</li>
 * </ul>
 */
@Component
public class DocumentReranker {

    private static final Logger log = LoggerFactory.getLogger(DocumentReranker.class);

    private static final int DEFAULT_TOP_K = 3;
    private static final String TEMPLATE_PATH = "prompts/document-score.st";

    private final ChatModel chatModel;
    private final PromptTemplateLoader templateLoader;

    public DocumentReranker(@Qualifier("dashscopeChatModel") ChatModel chatModel,
                            PromptTemplateLoader templateLoader) {
        this.chatModel = chatModel;
        this.templateLoader = templateLoader;
    }

    /**
     * 对召回文档重排序，返回 Top-K 个最相关文档。
     *
     * @param query     用户问题（或改写后的查询）
     * @param documents 向量检索召回的文档列表
     * @param topK      最终保留的文档数量
     * @return 按相关性降序排列的 Top-K 文档
     */
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

    /**
     * 使用默认 topK=3 重排序。
     */
    public List<Document> rerank(String query, List<Document> documents) {
        return rerank(query, documents, DEFAULT_TOP_K);
    }

    // -------------------- 私有方法 --------------------

    private double scoreDocument(String query, Document doc) {
        // 截断文档内容，避免 prompt 超过模型限制
        String content = doc.getFormattedContent();
        if (content.length() > 800) {
            content = content.substring(0, 800) + "...";
        }
        try {
            String promptText = templateLoader.render(TEMPLATE_PATH, Map.of("query", query, "content", content));
            String response = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
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