package com.example.zuoaiagent.config;

import com.example.zuoaiagent.chatmemory.DbBasedChatMemory;
import com.example.zuoaiagent.constants.SystemConstants;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;


@Configuration
public class ChatClientConfig {

    /** 触发摘要压缩的轮数阈值，可通过 application.yaml 中 chat.memory.summary-start-turns 配置 */
    @Value("${chat.memory.summary-start-turns:10}")
    private int summaryStartTurns;

    /** 历史记忆保留的最近消息条数，可通过 application.yaml 中 chat.memory.history-keep 配置 */
    @Value("${chat.memory.history-keep:20}")
    private int historyKeep;

    /**
     * DB 持久化对话记忆，支持摘要压缩。
     * 依赖 t_chat_memory 表（见 sql/chat-memory.sql）。
     *
     * <p>切换策略说明：
     * <ul>
     *   <li>开发/测试：可改为 {@code new MapBasedChatMemory()} 避免依赖数据库</li>
     *   <li>生产：使用本实现，持久化 + 自动摘要压缩</li>
     * </ul>
     */
    @Bean
    public ChatMemory chatMemory(JdbcTemplate jdbcTemplate,
                                 @Qualifier("dashscopeChatModel") ChatModel chatModel) {
        return new DbBasedChatMemory(jdbcTemplate, chatModel, summaryStartTurns, historyKeep);
    }

    @Bean
    public ChatClient chatClient(ChatModel dashscopeChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SystemConstants.SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
