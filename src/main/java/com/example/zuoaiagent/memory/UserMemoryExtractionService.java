package com.example.zuoaiagent.memory;

import com.example.zuoaiagent.chat.RoutingChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserMemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(UserMemoryExtractionService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final RoutingChatService routingChatService;

    public UserMemoryExtractionService(JdbcTemplate jdbcTemplate,
                                        RoutingChatService routingChatService) {
        this.jdbcTemplate = jdbcTemplate;
        this.routingChatService = routingChatService;
    }

    public void extractMemory(Long userId, String conversationId) {
        try {
            List<String> messages = jdbcTemplate.queryForList(
                    "SELECT content FROM t_chat_message_raw WHERE conversation_id = ? ORDER BY create_time DESC LIMIT 50",
                    String.class, conversationId);

            if (messages.isEmpty()) return;

            String prompt = "请从以下对话中提取用户偏好和习惯，输出JSON格式。只输出JSON，不要其他内容：\n" +
                    "{\"preferences\": [\"用户的偏好...\"], \"tech_stack\": [\"技术栈...\"], \"facts\": [\"记住的事实...\"]}\n\n" +
                    "对话内容：\n" + String.join("\n", messages);

            String result = routingChatService.chat(prompt, null, null, null);
            if (result == null) return;

            Map<String, Object> facts = parseJsonResult(result);
            if (facts.isEmpty()) return;

            String existing = queryExistingMemory(userId);
            Map<String, Object> merged = mergeMemory(existing, facts);
            upsertMemory(userId, merged);

            log.info("[UserMemoryExtraction] userId={} 记忆抽取完成", userId);
        } catch (Exception e) {
            log.warn("[UserMemoryExtraction] 记忆抽取失败: {}", e.getMessage());
        }
    }

    public String getMemoryContext(Long userId) {
        String memory = queryExistingMemory(userId);
        if (memory == null || memory.isBlank()) return null;
        try {
            Map<String, Object> facts = objectMapper.readValue(memory, new TypeReference<>() {});
            StringBuilder sb = new StringBuilder("[用户偏好]\n");
            if (facts.containsKey("preferences")) {
                sb.append("偏好: ").append(facts.get("preferences")).append("\n");
            }
            if (facts.containsKey("tech_stack")) {
                sb.append("技术栈: ").append(facts.get("tech_stack")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String queryExistingMemory(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT memory_json FROM t_user_memory_profile WHERE user_id = ?",
                String.class, userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMemory(String existing, Map<String, Object> newFacts) {
        if (existing == null || existing.isBlank()) return newFacts;
        try {
            Map<String, Object> old = objectMapper.readValue(existing, new TypeReference<>() {});
            for (Map.Entry<String, Object> entry : newFacts.entrySet()) {
                old.merge(entry.getKey(), entry.getValue(), (oldVal, newVal) -> {
                    if (oldVal instanceof List && newVal instanceof List) {
                        Set<Object> merged = new LinkedHashSet<>((List<Object>) oldVal);
                        merged.addAll((List<Object>) newVal);
                        return new ArrayList<>(merged);
                    }
                    return newVal;
                });
            }
            return old;
        } catch (Exception e) {
            return newFacts;
        }
    }

    private void upsertMemory(Long userId, Map<String, Object> facts) {
        try {
            String json = objectMapper.writeValueAsString(facts);
            int updated = jdbcTemplate.update(
                    "UPDATE t_user_memory_profile SET memory_json = ?, update_time = ? WHERE user_id = ?",
                    json, LocalDateTime.now(), userId);
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO t_user_memory_profile (id, user_id, memory_json, create_time) VALUES (?, ?, ?, ?)",
                        cn.hutool.core.util.IdUtil.getSnowflake(1, 3).nextId(), userId, json, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("[UserMemoryExtraction] 记忆持久化失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResult(String result) {
        try {
            String json = result.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json|```", "").trim();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}