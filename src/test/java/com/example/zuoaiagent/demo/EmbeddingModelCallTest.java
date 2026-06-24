package com.example.zuoaiagent.demo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

@SpringBootTest
@ActiveProfiles("local")
class EmbeddingModelCallTest {

    @Autowired
    private OllamaEmbeddingModel ollamaEmbeddingModel;

    @Autowired
    private OpenAiEmbeddingModel openAiEmbeddingModel;

    // ─────────────────── Ollama 本地模型 ───────────────────

    @Test
    void testOllamaQwen3Embedding() {
        callOllama("dengcao/Qwen3-Embedding-8B:F16");
    }

    @Test
    void testOlllamaNomicEmbedText() {
        callOllama("nomic-embed-text");
    }

    // ─────────────────── 硅基流动 OpenAI 兼容 ───────────────────

    @Test
    void testSiliconflowBgeM3() {
        callOpenAi("BAAI/bge-m3");
    }

    @Test
    void testSiliconflowQwen3Embedding() {
        callOpenAi("Qwen/Qwen3-Embedding-8B");
    }

    @Test
    void testSiliconflowBgeLargeZh() {
        callOpenAi("BAAI/bge-large-zh-v1.5");
    }

    @Test
    void testSiliconflowBceEmbedding() {
        callOpenAi("netease-youdao/bce-embedding-base_v1");
    }

    // ─────────────────── 工具方法 ───────────────────

    private void callOllama(String modelName) {
        System.out.println(">>> [Ollama] 测试模型: " + modelName);
        try {
            EmbeddingResponse response = ollamaEmbeddingModel.call(new EmbeddingRequest(
                    List.of("什么是向量数据库？"),
                    OllamaOptions.builder().model(modelName).build()
            ));
            float[] vector = response.getResults().get(0).getOutput();
            System.out.printf("✅ [Ollama]  %-40s 维度=%d  首维=%.4f%n", modelName, vector.length, vector[0]);
        } catch (Exception e) {
            System.out.printf("❌ [Ollama]  %-40s 失败: %s%n", modelName, causeChain(e));
        }
    }

    private void callOpenAi(String modelName) {
        System.out.println(">>> [SiliconFlow] 测试模型: " + modelName);
        try {
            EmbeddingResponse response = openAiEmbeddingModel.call(new EmbeddingRequest(
                    List.of("什么是向量数据库？"),
                    OpenAiEmbeddingOptions.builder().model(modelName).build()
            ));
            float[] vector = response.getResults().get(0).getOutput();
            System.out.printf("✅ [SiliconFlow] %-40s 维度=%d  首维=%.4f%n", modelName, vector.length, vector[0]);
        } catch (Exception e) {
            System.out.printf("❌ [SiliconFlow] %-40s 失败: %s%n", modelName, causeChain(e));
        }
    }

    /** 展开异常 cause chain，格式：ExceptionType: message → CauseType: message → ... */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        while (current != null) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
