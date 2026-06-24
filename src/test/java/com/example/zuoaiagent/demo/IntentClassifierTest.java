package com.example.zuoaiagent.demo;

import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.intent.service.IntentTreeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentClassifier 集成测试（启动 Spring 容器，调用真实 LLM）
 *
 * <p>运行前确保：
 * <ul>
 *   <li>DASHSCOPE_API_KEY 环境变量已设置</li>
 *   <li>数据库连接正常，t_intent_node 表有数据</li>
 * </ul>
 *
 * <p>执行命令：
 * <pre>mvn test -Dtest=IntentClassifierTest</pre>
 */
@SpringBootTest
class IntentClassifierTest {

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private IntentTreeService intentTreeService;

    // ==================== 意图树基础验证 ====================

    @Test
    @DisplayName("前置检查：意图树不为空")
    void testIntentTreeNotEmpty() {
        String treeText = intentTreeService.buildTreeText();
        System.out.println("===== 当前意图树 =====");
        System.out.println(treeText);
        System.out.println("====================");

        assertFalse(treeText.isBlank(), "意图树不应为空");
        assertNotEquals("（意图树为空）", treeText, "意图树内容不应为空占位符");
        System.out.println("[前置检查] 意图树加载正常，共 " + intentTreeService.getAllNodes().size() + " 个节点 ✓");
    }

    // ==================== 闲聊/系统节点 ====================

    @Test
    @DisplayName("场景1：问候语 → 命中系统节点")
    void testGreetingIsSystem() {
        IntentResult result = intentClassifier.classify("你好");
        printResult("你好", result);

        assertTrue(result.isSystem(), "问候语应命中系统节点");
        System.out.println("[场景1] 问候语正确识别为系统节点 ✓");
    }

    @Test
    @DisplayName("场景2：'你是谁' → 命中系统节点")
    void testWhoAreYouIsSystem() {
        IntentResult result = intentClassifier.classify("你是谁");
        printResult("你是谁", result);

        assertTrue(result.isSystem(), "'你是谁' 应命中系统节点");
        System.out.println("[场景2] '你是谁' 正确识别为系统节点 ✓");
    }

    // ==================== 业务意图 ====================

    @Test
    @DisplayName("场景3：业务问题 → 命中叶子节点，kbId 不为 null")
    void testBusinessQueryHasKbId() {
        // 根据你的意图树内容修改这个问题
        //String query = "如何进行股票估值分析";
        String query = "我想要了解宪法相关内容,他的核心是什么";
        IntentResult result = intentClassifier.classify(query);
        printResult(query, result);

        assertFalse(result.isSystem(), "业务问题不应命中系统节点");
        assertNotEquals(-1L, result.getIntentNodeId(), "业务问题不应返回 -1");
        assertNotNull(result.getKbId(), "叶子节点应有 kbId，当前命中节点: " + result.getLabel() + " nodeId=" + result.getIntentNodeId());
        assertTrue(result.getConfidence() > 0, "置信度应大于 0");
        System.out.println("[场景3] 业务问题命中叶子节点，kbId=" + result.getKbId() + " ✓");
    }

    @Test
    @DisplayName("场景4：模糊问题 → 不崩溃，返回有效结果")
    void testAmbiguousQueryNotCrash() {
        IntentResult result = intentClassifier.classify("随便问问");
        printResult("随便问问", result);

        assertNotNull(result, "模糊问题不应返回 null");
        System.out.println("[场景4] 模糊问题不崩溃，结果=" + result + " ✓");
    }

    @Test
    @DisplayName("场景5：空字符串 → 降级为 unknown")
    void testEmptyQueryReturnsUnknown() {
        IntentResult result = intentClassifier.classify("");
        printResult("（空字符串）", result);

        assertEquals(-1L, result.getIntentNodeId(), "空字符串应降级为 unknown(nodeId=-1)");
        assertEquals(0.0, result.getConfidence(), "unknown 置信度应为 0");
        System.out.println("[场景5] 空字符串正确降级为 unknown ✓");
    }

    // ==================== 工具方法 ====================

    private void printResult(String query, IntentResult result) {
        System.out.printf("  query     : %s%n", query);
        System.out.printf("  nodeId    : %s%n", result.getIntentNodeId());
        System.out.printf("  label     : %s%n", result.getLabel());
        System.out.printf("  confidence: %.2f%n", result.getConfidence());
        System.out.printf("  isSystem  : %s%n", result.isSystem());
        System.out.printf("  kbId      : %s%n", result.getKbId());
        System.out.println();
    }
}