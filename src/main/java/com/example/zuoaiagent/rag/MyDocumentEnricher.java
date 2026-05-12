package com.example.zuoaiagent.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MyDocumentEnricher {
    /**
     * 元数据增强
     */
    private final ChatModel chatModel;

    MyDocumentEnricher(@Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // 关键词元信息增强器 chunk中提取5个关键词存入metadata
    List<Document> enrichDocumentsByKeyword(List<Document> documents) {
        KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(this.chatModel, 5);
        return enricher.apply(documents);
    }

    // 摘要元信息增强器

    /**
     * 为每个 chunk 生成三种摘要并写入 metadata：
     *
     *   ┌─────────────┬─────────────────────┬───────────────────────────────────┐
     *   │ SummaryType │        含义         │               作用                │
     *   ├─────────────┼─────────────────────┼───────────────────────────────────┤
     *   │ CURRENT     │ 当前 chunk 的摘要   │ 提升当前块的语义密度              │
     *   ├─────────────┼─────────────────────┼───────────────────────────────────┤
     *   │ PREVIOUS    │ 上一个 chunk 的摘要 │ 补充上下文，解决跨 chunk 语义断裂 │
     *   ├─────────────┼─────────────────────┼───────────────────────────────────┤
     *   │ NEXT        │ 下一个 chunk 的摘要 │ 让当前块"预知"后续内容            │
     *   └─────────────┴─────────────────────┴───────────────────────────────────┘
     * @param documents
     * @return
     */
    List<Document> enrichDocumentsBySummary(List<Document> documents) {
        SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(chatModel,
                List.of(SummaryMetadataEnricher.SummaryType.PREVIOUS, SummaryMetadataEnricher.SummaryType.CURRENT, SummaryMetadataEnricher.SummaryType.NEXT));
        return enricher.apply(documents);
    }
}

