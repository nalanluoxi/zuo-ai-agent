package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.rag.MultiChannelRetriever;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 向量检索诊断测试
 *
 * <p>逐层排查搜不出来的原因：
 * <ol>
 *   <li>向量表里是否有数据</li>
 *   <li>kb_id 字段存的是什么格式</li>
 *   <li>全局检索（不加过滤）能否搜到</li>
 *   <li>加 kb_id 过滤后能否搜到</li>
 *   <li>MultiChannelRetriever 双通道结果</li>
 * </ol>
 *
 * <p>执行命令：
 * <pre>mvn test -Dtest=RetrieveDebugTest</pre>
 */
@SpringBootTest
class RetrieveDebugTest {

    private static final String QUERY = "世界宪法的产生与演变";
    private static final Long KB_ID = 2052030863243255810L;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private MultiChannelRetriever multiChannelRetriever;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 第一步：直接查数据库，确认数据存在 ====================

    @Test
    @DisplayName("诊断1：向量表总数据量")
    void checkTotalCount() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class);
        System.out.println("===== 向量表总行数: " + total + " =====");
        System.out.println("→ 如果是 0，说明文档从未成功入库，需要重新上传文档");
    }

    @Test
    @DisplayName("诊断2：查看 kb_id 字段实际存储的值（前10条）")
    void checkKbIdFormat() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, metadata->>'kb_id' AS kb_id, metadata->>'doc_name' AS doc_name " +
                "FROM vector_store LIMIT 10"
        );
        System.out.println("===== vector_store 前10条 metadata 中的 kb_id =====");
        if (rows.isEmpty()) {
            System.out.println("→ 表为空！文档未入库");
        }
        for (Map<String, Object> row : rows) {
            System.out.printf("  id=%-36s  kb_id=%-25s  doc_name=%s%n",
                    row.get("id"), row.get("kb_id"), row.get("doc_name"));
        }
        System.out.println("→ 注意：kb_id 存的值是否和 " + KB_ID + " 完全一致（类型、大小写）");
    }

    @Test
    @DisplayName("诊断3：直接 SQL 按 kb_id 过滤，确认数据存在")
    void checkKbIdInDb() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'kb_id' = ?",
                Long.class, KB_ID.toString()
        );
        System.out.println("===== kb_id=" + KB_ID + " 的文档块数量: " + count + " =====");
        if (count == 0) {
            // 查一下实际存的 kb_id 有哪些
            List<Map<String, Object>> kbIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT metadata->>'kb_id' AS kb_id, COUNT(*) AS cnt " +
                    "FROM vector_store GROUP BY metadata->>'kb_id'"
            );
            System.out.println("→ 向量表中实际存在的 kb_id 列表：");
            for (Map<String, Object> row : kbIds) {
                System.out.printf("    kb_id=%s  count=%s%n", row.get("kb_id"), row.get("cnt"));
            }
        } else {
            System.out.println("→ 数据存在，共 " + count + " 个文档块");
        }
    }

    // ==================== 第二步：向量检索逐层测试 ====================

    @Test
    @DisplayName("诊断4：全局检索（不加任何过滤）")
    void checkGlobalSearch() {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(QUERY)
                        .topK(5)
                        .build()
        );
        System.out.println("===== 全局检索结果（topK=5）=====");
        System.out.println("query: " + QUERY);
        System.out.println("命中数: " + docs.size());
        if (docs.isEmpty()) {
            System.out.println("→ 全局检索也搜不到，说明：");
            System.out.println("  1. 向量表为空（文档未入库）");
            System.out.println("  2. 或 embedding 模型不一致（入库时用A模型，检索时用B模型）");
        }
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String content = d.getFormattedContent();
            System.out.printf("  [%d] kb_id=%s  preview=%s%n",
                    i + 1,
                    d.getMetadata().get("kb_id"),
                    content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
        }
    }

    @Test
    @DisplayName("诊断5：加 kb_id 过滤的向量检索")
    void checkFilteredSearch() {
        org.springframework.ai.vectorstore.filter.FilterExpressionBuilder b =
                new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder();

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(QUERY)
                        .topK(5)
                        .filterExpression(b.eq("kb_id", KB_ID.toString()).build())
                        .build()
        );
        System.out.println("===== 加 kb_id 过滤的检索结果 =====");
        System.out.println("query: " + QUERY);
        System.out.println("kb_id: " + KB_ID);
        System.out.println("命中数: " + docs.size());
        if (docs.isEmpty()) {
            System.out.println("→ 加过滤后搜不到，原因：");
            System.out.println("  1. 该 kb_id 下没有文档（诊断3 验证）");
            System.out.println("  2. 或相似度阈值太高，所有文档都被过滤掉");
        }
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String content = d.getFormattedContent();
            System.out.printf("  [%d] kb_id=%s  preview=%s%n",
                    i + 1,
                    d.getMetadata().get("kb_id"),
                    content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
        }
    }

    @Test
    @DisplayName("诊断6：降低相似度阈值重试（similarityThreshold=0.0）")
    void checkWithLowThreshold() {
        org.springframework.ai.vectorstore.filter.FilterExpressionBuilder b =
                new org.springframework.ai.vectorstore.filter.FilterExpressionBuilder();

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(QUERY)
                        .topK(5)
                        .similarityThreshold(0.0)   // 关掉相似度过滤，全部返回
                        .filterExpression(b.eq("kb_id", KB_ID.toString()).build())
                        .build()
        );
        System.out.println("===== similarityThreshold=0.0 + kb_id 过滤 =====");
        System.out.println("命中数: " + docs.size());
        if (!docs.isEmpty()) {
            System.out.println("→ 说明相似度阈值过高导致被过滤，需要降低 similarityThreshold");
        }
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String content = d.getFormattedContent();
            System.out.printf("  [%d] score=? kb_id=%s  preview=%s%n",
                    i + 1,
                    d.getMetadata().get("kb_id"),
                    content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
        }
    }

    // ==================== 第三步：MultiChannelRetriever 整体测试 ====================

    @Test
    @DisplayName("诊断7：MultiChannelRetriever 双通道完整测试")
    void checkMultiChannelRetriever() {
        System.out.println("===== MultiChannelRetriever 双通道检索 =====");
        System.out.println("query: " + QUERY);
        System.out.println("kbId:  " + KB_ID);

        List<Document> docs = multiChannelRetriever.retrieve(QUERY, KB_ID);

        System.out.println("最终合并结果数: " + docs.size());
        if (docs.isEmpty()) {
            System.out.println("→ 两个通道都没搜到，请先跑诊断1~6 确认根因");
        }
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String content = d.getFormattedContent();
            System.out.printf("  [%d] kb_id=%s  preview=%s%n",
                    i + 1,
                    d.getMetadata().get("kb_id"),
                    content != null && content.length() > 150 ? content.substring(0, 150) + "..." : content);
        }
    }
}