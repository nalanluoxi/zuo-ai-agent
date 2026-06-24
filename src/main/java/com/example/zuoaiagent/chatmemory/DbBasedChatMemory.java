package com.example.zuoaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 PostgreSQL 的持久化对话记忆，支持摘要压缩。
 *
 * <p>存储表：t_chat_memory（由 chat-memory.sql 初始化）
 *
 * <p>摘要压缩策略：
 * <ul>
 *   <li>每次 {@link #add} 写入后检查该会话消息总数</li>
 *   <li>消息数超过 {@code summaryStartTurns * 2}（每轮一问一答）时触发压缩</li>
 *   <li>压缩：将最旧的一半消息调用 LLM 生成摘要，以 SystemMessage 形式存回数据库，删除被摘要的原始消息</li>
 * </ul>
 */
@Slf4j
public class DbBasedChatMemory implements ChatMemory {


    /** 触发摘要压缩的轮数阈值（每轮 = 1 user + 1 assistant），默认 10 轮 */
    private static final int DEFAULT_SUMMARY_START_TURNS = 10;

    /** 历史保留的最近 N 条消息 */
    private static final int DEFAULT_HISTORY_KEEP = 20;

    private final JdbcTemplate jdbcTemplate;
    private final ChatModel chatModel;
    private final int summaryStartTurns;
    private final int historyKeep;

    public DbBasedChatMemory(JdbcTemplate jdbcTemplate, ChatModel chatModel) {
        this(jdbcTemplate, chatModel, DEFAULT_SUMMARY_START_TURNS, DEFAULT_HISTORY_KEEP);
    }

    public DbBasedChatMemory(JdbcTemplate jdbcTemplate, ChatModel chatModel,
                              int summaryStartTurns, int historyKeep) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatModel = chatModel;
        this.summaryStartTurns = summaryStartTurns;
        this.historyKeep = historyKeep;
    }

    // ─────────────────────────── ChatMemory 接口 ───────────────────────────

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            String role = message.getMessageType().getValue();
            String content = message.getText();
            jdbcTemplate.update(
                    "INSERT INTO t_chat_memory (conversation_id, role, content) VALUES (?, ?, ?)",
                    conversationId, role, content
            );
        }
        tryCompress(conversationId);
    }

    @Override
    public void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    @Override
    public List<Message> get(String conversationId) {
        return get(conversationId, historyKeep);
    }

    /**
     * 获取最近 lastN 条消息。
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> all = loadAll(conversationId);
        int size = all.size();
        return all.subList(Math.max(0, size - lastN), size);
    }

    @Override
    public void clear(String conversationId) {
        jdbcTemplate.update(
                "DELETE FROM t_chat_memory WHERE conversation_id = ?",
                conversationId
        );
        log.info("[DbBasedChatMemory] 清除会话 {} 的全部消息", conversationId);
    }

    // ─────────────────────────── 私有方法 ───────────────────────────

    /** 从数据库按时间顺序加载该会话的全部消息。 */
    private List<Message> loadAll(String conversationId) {
        return jdbcTemplate.query(
                "SELECT role, content FROM t_chat_memory WHERE conversation_id = ? ORDER BY id ASC",
                (rs, rowNum) -> {
                    String role = rs.getString("role");
                    String content = rs.getString("content");
                    return toMessage(role, content);
                },
                conversationId
        );
    }

    /** 将数据库中的 role + content 还原为 Spring AI Message 对象。 */
    private Message toMessage(String role, String content) {
        return switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    /**
     * 检查是否需要触发摘要压缩。
     *
     * <p>当总消息数超过 {@code summaryStartTurns * 2} 时，
     * 将最旧的一半消息摘要后替换为单条 SystemMessage。
     */
    private void tryCompress(String conversationId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_memory WHERE conversation_id = ?",
                Integer.class, conversationId
        );
        if (total == null || total <= summaryStartTurns * 2) {
            return;
        }

        log.info("[DbBasedChatMemory] 会话 {} 消息数={}，触发摘要压缩", conversationId, total);

        // 找出最旧的 half 条消息的 id 范围
        int half = total / 2;
        List<Long> oldIds = jdbcTemplate.queryForList(
                "SELECT id FROM t_chat_memory WHERE conversation_id = ? ORDER BY id ASC LIMIT ?",
                Long.class, conversationId, half
        );
        if (oldIds.isEmpty()) return;

        // 加载这些旧消息的内容，生成摘要
        List<Message> oldMessages = jdbcTemplate.query(
                "SELECT role, content FROM t_chat_memory WHERE id IN (" +
                        oldIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0") +
                        ") ORDER BY id ASC",
                (rs, rowNum) -> toMessage(rs.getString("role"), rs.getString("content"))
        );

        String summary = generateSummary(oldMessages);
        if (summary == null || summary.isBlank()) {
            log.warn("[DbBasedChatMemory] 摘要生成为空，跳过压缩");
            return;
        }

        // 删除旧消息，插入摘要
        jdbcTemplate.update(
                "DELETE FROM t_chat_memory WHERE id IN (" +
                        oldIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0") + ")"
        );
        jdbcTemplate.update(
                "INSERT INTO t_chat_memory (conversation_id, role, content) VALUES (?, ?, ?)",
                conversationId, MessageType.SYSTEM.getValue(),
                "[对话摘要]\n" + summary
        );
        log.info("[DbBasedChatMemory] 会话 {} 压缩完成，删除 {} 条旧消息，写入摘要", conversationId, oldIds.size());
    }

    /** 调用 LLM 对消息列表生成摘要。 */
    private String generateSummary(List<Message> messages) {
        if (messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            sb.append(msg.getMessageType().getValue()).append(": ").append(msg.getText()).append("\n");
        }
        String summarizePrompt = "请对以下对话内容进行简洁摘要（不超过200字），保留关键信息和结论：\n\n" + sb;
        try {
            return chatModel.call(new Prompt(summarizePrompt))
                    .getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("[DbBasedChatMemory] 摘要生成失败: {}", e.getMessage());
            return null;
        }
    }
}