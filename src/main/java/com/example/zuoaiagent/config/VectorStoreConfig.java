package com.example.zuoaiagent.config;

import com.example.zuoaiagent.rag.DocumentLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Resource
    private DocumentLoader documentLoader;

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public ApplicationRunner loadDocumentsToVectorStore(VectorStore vectorStore) {
        return args -> {
            List<Document> documents = documentLoader.loadMarkdowns();
            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                log.info("已加载 {} 篇 Markdown 文档到向量库", documents.size());
            }
        };
    }
}