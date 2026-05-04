package com.example.zuoaiagent.config;


import com.example.zuoaiagent.rag.DocumentLoader;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class VectorStoreConfig {


    @Resource
    private DocumentLoader documentLoader;

    @Bean
    VectorStore vectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        // 加载文档
       List<Document> documents = documentLoader.loadMarkdowns();
       // List<Document> documents = documentLoader.loadPdfs();
        if (!documents.isEmpty()) {
            simpleVectorStore.add(documents);
        }
        return simpleVectorStore;
    }
}


