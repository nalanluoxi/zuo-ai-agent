package com.example.zuoaiagent.config;

import com.example.zuoaiagent.knowledge.embedding.CircuitBreakerEmbeddingModel;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingCircuitBreaker;
import com.example.zuoaiagent.knowledge.embedding.EmbeddingModelFactory;
import com.example.zuoaiagent.rag.DocumentLoader;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * PgVector 向量库配置类
 *
 * <p>负责初始化 {@link VectorStore} Bean。向量化层使用
 * {@link CircuitBreakerEmbeddingModel} 包装 {@link EmbeddingModelFactory} 中注册的
 * 多厂商候选模型，实现跨厂商熔断与故障切换。向量维度为 4096（Qwen3-Embedding-8B）。
 *
 * <p>文档入库已改为动态方式，由上传接口触发
 * {@link com.example.zuoaiagent.knowledge.ingestion.DocumentIngestionService} 异步处理，
 * 启动时不再预加载 Markdown 文档（{@link DocumentLoader#loadMarkdowns()} 已注释内容）。
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Resource
    private DocumentLoader documentLoader;

    /**
     * 构建 {@link CircuitBreakerEmbeddingModel} Bean。
     *
     * <p>此 Bean 覆盖 Spring AI 自动装配的默认 EmbeddingModel，
     * 成为 {@link VectorStore} 的实际向量化实现，使整个写入路径受熔断保护。
     *
     * @param factory        Embedding 模型工厂（持有多厂商候选队列）
     * @param circuitBreaker 熔断器
     * @return 带熔断的 Embedding 模型
     */
    @Bean
    public CircuitBreakerEmbeddingModel circuitBreakerEmbeddingModel(
            EmbeddingModelFactory factory,
            EmbeddingCircuitBreaker circuitBreaker) {
        return new CircuitBreakerEmbeddingModel(factory, circuitBreaker);
    }

    /**
     * 构建 PgVector 向量存储 Bean。
     *
     * <p>参数说明：
     * <ul>
     *   <li>dimensions=4096：与 Qwen3-Embedding-8B 输出维度一致</li>
     *   <li>distanceType=COSINE_DISTANCE：余弦相似度，适合文本语义检索</li>
     *   <li>indexType=HNSW：近似最近邻索引，检索性能优于精确扫描</li>
     *   <li>initializeSchema=true：首次启动自动建表，无需手动 DDL</li>
     * </ul>
     *
     * @param jdbcTemplate                Spring 管理的 JDBC 操作模板
     * @param circuitBreakerEmbeddingModel 带熔断的 Embedding 模型
     * @return 配置好的 PgVectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   CircuitBreakerEmbeddingModel circuitBreakerEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, circuitBreakerEmbeddingModel)
                .dimensions(4096)                              // Qwen3-Embedding-8B 维度为 4096
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    /**
     * 启动时预加载 Markdown 文档到向量库（可选钩子）。
     *
     * <p>当前 Markdown 加载已在 DocumentLoader 内部注释掉；此处仅保留框架入口。
     * 正式文档入库请通过上传接口触发 DocumentIngestionService。
     *
     * @param vectorStore 向量存储 Bean
     * @return Spring Boot ApplicationRunner，容器就绪后执行
     */
   // @Bean
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
