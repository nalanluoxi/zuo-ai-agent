package com.example.zuoaiagent.knowledge.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档入库流水线配置属性，绑定 {@code application.yaml} 中的 {@code ingestion} 前缀。
 *
 * <pre>{@code
 * ingestion:
 *   chunking:
 *     chunk-size: 512
 *     overlap-size: 128
 *   enrichment:
 *     enable-summary: false
 *     enable-keyword: true
 *     keyword-count: 5
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {

    private Chunking chunking = new Chunking();
    private Enrichment enrichment = new Enrichment();

    public Chunking getChunking() { return chunking; }
    public void setChunking(Chunking chunking) { this.chunking = chunking; }

    public Enrichment getEnrichment() { return enrichment; }
    public void setEnrichment(Enrichment enrichment) { this.enrichment = enrichment; }

    public static class Chunking {
        /** 默认分块大小（字符数）。默认 512。 */
        private int chunkSize = 512;

        /** 相邻块重叠大小（字符数）。默认 128。 */
        private int overlapSize = 128;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

        public int getOverlapSize() { return overlapSize; }
        public void setOverlapSize(int overlapSize) { this.overlapSize = overlapSize; }
    }

    public static class Enrichment {
        /** 是否开启摘要元数据增强（PREVIOUS/CURRENT/NEXT 三种摘要）。默认关闭。 */
        private boolean enableSummary = false;

        /** 是否开启关键词元数据增强。默认开启。 */
        private boolean enableKeyword = true;

        /** 提取关键词数量。默认 5。 */
        private int keywordCount = 5;

        public boolean isEnableSummary() { return enableSummary; }
        public void setEnableSummary(boolean enableSummary) { this.enableSummary = enableSummary; }

        public boolean isEnableKeyword() { return enableKeyword; }
        public void setEnableKeyword(boolean enableKeyword) { this.enableKeyword = enableKeyword; }

        public int getKeywordCount() { return keywordCount; }
        public void setKeywordCount(int keywordCount) { this.keywordCount = keywordCount; }
    }
}