package com.example.zuoaiagent.knowledge.chunk;

/**
 * 文本分块策略枚举
 *
 * <p>定义了两种分块模式，由 {@link DocumentIngestionService} 根据文件类型自动选择：
 * <ul>
 *   <li>{@link #FIXED_SIZE}：固定字符数滑动窗口，适合纯文本/PDF</li>
 *   <li>{@link #STRUCTURE_AWARE}：识别 Markdown 结构后按语义块打包，适合 Markdown 文档</li>
 * </ul>
 *
 * <p>参照 ragent 的 {@code com.nageoffer.ai.ragent.core.chunk.ChunkingMode}。
 */
public enum ChunkingMode {

    /**
     * 固定大小分块：按 chunkSize 字符滑动，相邻块保留 overlapSize 重叠。
     * 默认参数：chunkSize=512，overlapSize=128。
     */
    FIXED_SIZE,

    /**
     * 结构感知分块：识别 Heading / CodeFence / Atomic / Para 块，
     * 按 min/target/max 预算打包，保持 Markdown 语义完整性。
     * 默认参数：target=1400，max=1800，min=600。
     */
    STRUCTURE_AWARE
}
