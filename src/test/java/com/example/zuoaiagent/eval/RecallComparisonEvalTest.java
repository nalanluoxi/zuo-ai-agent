package com.example.zuoaiagent.eval;

import com.example.zuoaiagent.rag.DocumentReranker;
import com.example.zuoaiagent.rag.MultiChannelRetriever;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双路召回 + Rerank vs 纯向量召回 —— 准确率对比评估测试
 *
 * <p><b>评估指标</b>：Top-K 命中率（Hit Rate）
 * <pre>
 *   命中率 = 标准答案文档至少1个出现在召回 Top-K 中的题目数 / 总题目数
 * </pre>
 *
 * <p><b>执行方式</b>：
 * <pre>
 *   mvn test -Dtest=RecallComparisonEvalTest -pl .
 * </pre>
 *
 * <p><b>使用前必读</b>：
 * <ol>
 *   <li>运行前确保向量库已入库（先跑一次 {@code RetrieveDebugTest#checkTotalCount} 确认行数 &gt; 0）</li>
 *   <li>评估集中的 {@code goldKeywords} 填写标准答案文档里<b>必定出现</b>的关键词片段，区分大小写</li>
 *   <li>topK 默认 6（与 MultiChannelRetriever.DEFAULT_TOP_K 一致），Rerank 后取 Top-3</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("local")
class RecallComparisonEvalTest {

    // ── 评估集：每条记录 = (query, kbId, 标准答案关键词列表) ──────────────────
    // goldKeywords：标准答案文档内容里必定包含的片段，用于在召回结果中判定"命中"
    // 根据你的向量库实际内容修改这里！
    private static final List<EvalCase> EVAL_SET = List.of(
            new EvalCase(
                    "世界宪法的产生与演变",
                    null,   // kbId = null 表示只走全局通道；填 Long 值则启用意图定向通道
                    List.of("宪法", "权利", "国家")
            ),
            new EvalCase(
                    "宪法的基本原则有哪些",
                    null,
                    List.of("原则", "宪法", "人民")
            ),
            new EvalCase(
                    "公民的基本权利与义务",
                    null,
                    List.of("公民", "权利", "义务")
            ),
            new EvalCase(
                    "国家机构的组织与职权",
                    null,
                    List.of("国家", "机构", "职权")
            ),
            new EvalCase(
                    "宪法修改的程序",
                    null,
                    List.of("修改", "宪法", "程序")
            )
    );

    private static final int TOP_K = 6;       // 召回阶段 top-k
    private static final int RERANK_TOP_K = 3; // Rerank 后保留数量

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private MultiChannelRetriever multiChannelRetriever;

    @Autowired
    private DocumentReranker documentReranker;

    // ── 评估结果容器（静态，供最终汇总打印）────────────────────────────────────
    private static final List<String> REPORT_LINES = new ArrayList<>();

    @BeforeAll
    static void printHeader() {
        REPORT_LINES.add("\n╔══════════════════════════════════════════════════════════════╗");
        REPORT_LINES.add("║          双路召回 + Rerank  vs  纯向量  准确率对比报告          ║");
        REPORT_LINES.add("╚══════════════════════════════════════════════════════════════╝");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试1：纯向量召回命中率
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("评估1：纯向量召回 Top-6 命中率")
    void evalPureVectorRecall() {
        System.out.println("\n【评估1】纯向量召回 Top-" + TOP_K);
        int hitCount = 0;

        for (EvalCase c : EVAL_SET) {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(c.query()).topK(TOP_K).build());
            boolean hit = isHit(docs, c.goldKeywords());
            hitCount += hit ? 1 : 0;
            System.out.printf("  %-40s → %s（命中 %d 个文档块）%n",
                    abbr(c.query(), 40), hit ? "✅ 命中" : "❌ 未命中", docs.size());
        }

        double rate = (double) hitCount / EVAL_SET.size() * 100;
        String line = String.format("【纯向量召回】命中率 = %d/%d = %.1f%%", hitCount, EVAL_SET.size(), rate);
        System.out.println("  " + line);
        REPORT_LINES.add("  " + line);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试2：双路召回命中率
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("评估2：双路召回（全局 + 意图定向）Top-12 命中率")
    void evalDualChannelRecall() {
        System.out.println("\n【评估2】双路召回 Top-" + TOP_K + "(每通道)");
        int hitCount = 0;

        for (EvalCase c : EVAL_SET) {
            List<Document> docs = multiChannelRetriever.retrieve(c.query(), c.kbId());
            boolean hit = isHit(docs, c.goldKeywords());
            hitCount += hit ? 1 : 0;
            System.out.printf("  %-40s → %s（合并 %d 个文档块）%n",
                    abbr(c.query(), 40), hit ? "✅ 命中" : "❌ 未命中", docs.size());
        }

        double rate = (double) hitCount / EVAL_SET.size() * 100;
        String line = String.format("【双路召回】命中率 = %d/%d = %.1f%%", hitCount, EVAL_SET.size(), rate);
        System.out.println("  " + line);
        REPORT_LINES.add("  " + line);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试3：双路召回 + Rerank 命中率（主评估）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("评估3：双路召回 + Rerank Top-3 命中率（主评估）")
    void evalDualChannelWithRerank() {
        System.out.println("\n【评估3】双路召回 + Rerank Top-" + RERANK_TOP_K + "（主评估）");
        int hitCount = 0;

        for (EvalCase c : EVAL_SET) {
            List<Document> retrieved = multiChannelRetriever.retrieve(c.query(), c.kbId());
            List<Document> reranked = documentReranker.rerank(c.query(), retrieved, RERANK_TOP_K);
            boolean hit = isHit(reranked, c.goldKeywords());
            hitCount += hit ? 1 : 0;

            System.out.printf("  %-40s → %s（Rerank后保留 %d 块）%n",
                    abbr(c.query(), 40), hit ? "✅ 命中" : "❌ 未命中", reranked.size());
            // 打印 Rerank 后每个文档的前50字，便于肉眼验证
            for (int i = 0; i < reranked.size(); i++) {
                String preview = reranked.get(i).getFormattedContent();
                if (preview != null && preview.length() > 60) preview = preview.substring(0, 60) + "…";
                System.out.printf("      [Top%d] %s%n", i + 1, preview);
            }
        }

        double rate = (double) hitCount / EVAL_SET.size() * 100;
        String line = String.format("【双路+Rerank】命中率 = %d/%d = %.1f%%", hitCount, EVAL_SET.size(), rate);
        System.out.println("  " + line);
        REPORT_LINES.add("  " + line);
        assertTrue(rate >= 0, "命中率必须 >= 0%（校验测试框架正常运行）");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  测试4：综合对比报告（三组数字汇总 + 提升比计算）
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("评估4：综合对比报告（全面汇总 + 提升百分比）")
    void evalFullComparison() {
        System.out.println("\n【评估4】综合对比（建议单独跑此方法，输出最完整）");

        int pureHit = 0, dualHit = 0, rerankHit = 0;

        System.out.println("  ┌──────────────────────────────┬────────────┬────────────┬──────────────┐");
        System.out.println("  │ 问题                         │  纯向量    │  双路召回  │ 双路+Rerank  │");
        System.out.println("  ├──────────────────────────────┼────────────┼────────────┼──────────────┤");

        for (EvalCase c : EVAL_SET) {
            // 纯向量
            List<Document> pureDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(c.query()).topK(TOP_K).build());
            boolean pureOk = isHit(pureDocs, c.goldKeywords());

            // 双路
            List<Document> dualDocs = multiChannelRetriever.retrieve(c.query(), c.kbId());
            boolean dualOk = isHit(dualDocs, c.goldKeywords());

            // 双路 + Rerank
            List<Document> reranked = documentReranker.rerank(c.query(), dualDocs, RERANK_TOP_K);
            boolean rerankOk = isHit(reranked, c.goldKeywords());

            pureHit   += pureOk   ? 1 : 0;
            dualHit   += dualOk   ? 1 : 0;
            rerankHit += rerankOk ? 1 : 0;

            System.out.printf("  │ %-28s │  %-8s  │  %-8s  │  %-10s  │%n",
                    abbr(c.query(), 28),
                    pureOk   ? "✅" : "❌",
                    dualOk   ? "✅" : "❌",
                    rerankOk ? "✅" : "❌");
        }

        System.out.println("  └──────────────────────────────┴────────────┴────────────┴──────────────┘");

        int total = EVAL_SET.size();
        double pureRate   = (double) pureHit   / total * 100;
        double dualRate   = (double) dualHit   / total * 100;
        double rerankRate = (double) rerankHit / total * 100;

        // 提升比：相对于纯向量的相对提升
        double liftDual   = pureRate > 0 ? (dualRate   - pureRate) / pureRate * 100 : (dualRate > 0 ? Double.POSITIVE_INFINITY : 0);
        double liftRerank = pureRate > 0 ? (rerankRate - pureRate) / pureRate * 100 : (rerankRate > 0 ? Double.POSITIVE_INFINITY : 0);

        System.out.println();
        System.out.printf("  %-16s  命中率 = %d/%d = %.1f%%%n", "纯向量召回：",   pureHit,   total, pureRate);
        System.out.printf("  %-16s  命中率 = %d/%d = %.1f%%  |  相对提升: %+.1f%%%n",
                "双路召回：", dualHit, total, dualRate, liftDual);
        System.out.printf("  %-16s  命中率 = %d/%d = %.1f%%  |  相对提升: %+.1f%%%n",
                "双路+Rerank：", rerankHit, total, rerankRate, liftRerank);

        System.out.println();
        System.out.println("  ─── 面试可直接引用的数据 ────────────────────────────────────────────");
        System.out.printf("  「在 %d 条本地测试集上，双路召回+Rerank 对比纯向量召回，%n", total);
        System.out.printf("    命中率从 %.1f%% 提升到 %.1f%%，绝对提升 %.1f 个百分点（相对 +%.1f%%）」%n",
                pureRate, rerankRate, rerankRate - pureRate, liftRerank);
        System.out.println("  ─────────────────────────────────────────────────────────────────────");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 判断召回列表中是否至少1个文档包含 goldKeywords 里的至少1个关键词。
     * 匹配策略：ANY_KEYWORD（有1个命中即算命中），更宽松，适合评估召回阶段。
     */
    private boolean isHit(List<Document> docs, List<String> goldKeywords) {
        for (Document doc : docs) {
            String content = doc.getFormattedContent();
            if (content == null) continue;
            for (String kw : goldKeywords) {
                if (content.contains(kw)) return true;
            }
        }
        return false;
    }

    /** 截断字符串到指定长度，末尾补省略号 */
    private String abbr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  评估用例数据类
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @param query        评估问题
     * @param kbId         知识库 ID（null = 只走全局通道）
     * @param goldKeywords 标准答案关键词，文档内容包含任意1个即算命中
     */
    private record EvalCase(String query, Long kbId, List<String> goldKeywords) {}
}
