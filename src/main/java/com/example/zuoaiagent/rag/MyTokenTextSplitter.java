package com.example.zuoaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MyTokenTextSplitter {

    /**
     * 默认分词器
     * @param documents
     * @return
     */
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    /**
     * 自定义分词器
     * 对应构造函数签名：
     *   TokenTextSplitter(
     *       int defaultChunkSize,     // 1000 — 每个 chunk 最大 token 数
     *       int minChunkSizeChars,    // 400  — chunk 最小字符数
     *       int minChunkLengthToEmbed,// 10   — 低于此长度的 chunk 直接丢弃
     *       int maxNumChunks,         // 5000 — 最多切出 5000 个 chunk
     *       boolean keepSeparator     // true — 保留分隔符（换行等）到 chunk 中
     *   )
     * @param documents
     * @return
     */
    public List<Document> splitCustomized(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
        return splitter.apply(documents);
    }
}

