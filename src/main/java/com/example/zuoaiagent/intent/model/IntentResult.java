package com.example.zuoaiagent.intent.model;

/**
 * 意图分类结果 VO
 */
public class IntentResult {

    /** 匹配到的意图节点 ID；-1 表示未知/闲聊 */
    private final Long intentNodeId;

    /** 节点标签名称 */
    private final String label;

    /** 分类置信度（0.0~1.0） */
    private final double confidence;

    /** 是否系统/闲聊节点（true=短路，不走向量检索） */
    private final boolean isSystem;

    /** 关联知识库 ID（可为 null） */
    private final Long kbId;

    public IntentResult(Long intentNodeId, String label, double confidence, boolean isSystem, Long kbId) {
        this.intentNodeId = intentNodeId;
        this.label = label;
        this.confidence = confidence;
        this.isSystem = isSystem;
        this.kbId = kbId;
    }

    /** 未知意图（降级：置信度 0，走全局检索） */
    public static IntentResult unknown() {
        return new IntentResult(-1L, "unknown", 0.0, false, null);
    }

    /** 系统/闲聊意图（短路，不走向量检索） */
    public static IntentResult system(Long nodeId, String label) {
        return new IntentResult(nodeId, label, 1.0, true, null);
    }

    public Long getIntentNodeId() { return intentNodeId; }
    public String getLabel() { return label; }
    public double getConfidence() { return confidence; }
    public boolean isSystem() { return isSystem; }
    public Long getKbId() { return kbId; }

    @Override
    public String toString() {
        return "IntentResult{nodeId=" + intentNodeId + ", label='" + label + "', confidence=" + confidence
                + ", isSystem=" + isSystem + ", kbId=" + kbId + "}";
    }
}
