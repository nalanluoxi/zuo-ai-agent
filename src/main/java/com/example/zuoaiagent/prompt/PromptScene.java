package com.example.zuoaiagent.prompt;

/**
 * Prompt 场景枚举
 *
 * <ul>
 *   <li>{@link #KB_ONLY} — 检索到知识库内容，使用 RAG 回答模板</li>
 *   <li>{@link #SYSTEM_CHAT} — 闲聊/系统问题短路，使用通用对话模板</li>
 *   <li>{@link #EMPTY_RETRIEVAL} — 检索无结果，告知用户未找到相关内容</li>
 * </ul>
 */
public enum PromptScene {

    /** 检索到知识库内容 */
    KB_ONLY,

    /** 闲聊或系统类问题，不走检索 */
    SYSTEM_CHAT,

    /** 检索无结果兜底 */
    EMPTY_RETRIEVAL
}
