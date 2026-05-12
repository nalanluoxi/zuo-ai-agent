package com.example.zuoaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MyVectorStoreWriter {
    private final VectorStore vectorStore;

    MyVectorStoreWriter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void storeDocuments(List<Document> documents) {
        vectorStore.accept(documents);
    }
}
