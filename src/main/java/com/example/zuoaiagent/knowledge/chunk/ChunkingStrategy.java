package com.example.zuoaiagent.knowledge.chunk;

import java.util.List;

/**
 * 文本分块策略接口
 *
 * <p>所有具体分块器均实现此接口，通过 {@link #getType()} 返回对应的 {@link ChunkingMode}，
 * 便于按类型查找策略 Bean（参照 ragent {@code ChunkingStrategy}）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>策略无状态，可作为 Spring 单例 Bean 安全地并发调用</li>
 *   <li>输入文本为已解析的纯文本/Markdown 字符串，不含二进制数据</li>
 *   <li>返回的列表不包含空字符串或仅空白的片段</li>
 * </ul>
 */
public interface ChunkingStrategy {

    /**
     * 将输入文本切分为若干文本块。
     *
     * @param text      待分块的原始文本（非空）
     * @param chunkSize 目标块大小（字符数），含义由具体策略解释
     * @param overlapSize 相邻块重叠字符数（仅固定大小策略使用）
     * @return 有序文本块列表，每个元素对应一个向量化单元
     */
    List<String> chunk(String text, int chunkSize, int overlapSize);

    /**
     * 返回该策略对应的分块模式枚举值，用于按类型查找具体实现。
     *
     * @return 策略类型
     */
    ChunkingMode getType();
}
