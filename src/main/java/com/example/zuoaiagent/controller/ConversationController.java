package com.example.zuoaiagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * P22 增强：对话列表（分页、搜索、按用户过滤）
     */
    @GetMapping
    public BaseResponse<Map<String, Object>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String userId) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        try {
            // 构建查询 SQL
            StringBuilder sql = new StringBuilder(
                "SELECT id, title, user_id, created_at, updated_at, " +
                "(SELECT COUNT(*) FROM t_chat_message_raw WHERE conversation_id = t.id) as message_count " +
                "FROM t_conversation t WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();
            
            // 搜索条件
            if (search != null && !search.trim().isEmpty()) {
                sql.append(" AND (title LIKE ? OR id LIKE ?)");
                String searchParam = "%" + search.trim() + "%";
                params.add(searchParam);
                params.add(searchParam);
            }
            
            // 用户过滤条件
            if (userId != null && !userId.trim().isEmpty()) {
                sql.append(" AND user_id = ?");
                params.add(userId);
            }
            
            // 排序和分页
            sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((page - 1) * size);
            
            // 查询数据
            List<Map<String, Object>> content = jdbcTemplate.queryForList(
                sql.toString(),
                params.toArray()
            );
            
            // 获取总数
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) as total FROM t_conversation WHERE 1=1");
            List<Object> countParams = new ArrayList<>();
            
            if (search != null && !search.trim().isEmpty()) {
                countSql.append(" AND (title LIKE ? OR id LIKE ?)");
                String searchParam = "%" + search.trim() + "%";
                countParams.add(searchParam);
                countParams.add(searchParam);
            }
            
            if (userId != null && !userId.trim().isEmpty()) {
                countSql.append(" AND user_id = ?");
                countParams.add(userId);
            }
            
            Integer total = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, countParams.toArray());
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("totalElements", total);
            result.put("totalPages", (total + size - 1) / size);
            result.put("currentPage", page);
            result.put("pageSize", size);
            
            return ResultUtils.success(result);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取对话列表失败：" + e.getMessage());
        }
    }

    /**
     * P22 增强：删除对话及其关联的所有消息
     */
    @DeleteMapping("/{conversationId}")
    public BaseResponse<Boolean> deleteConversation(
            @PathVariable String conversationId) {
        
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对话ID不能为空");
        }
        
        try {
            // 删除该对话的所有消息
            String deleteMessagesSql = "DELETE FROM t_chat_message_raw WHERE conversation_id = ?";
            jdbcTemplate.update(deleteMessagesSql, conversationId);
            
            // 删除对话本身
            String deleteConvSql = "DELETE FROM t_conversation WHERE id = ?";
            int rows = jdbcTemplate.update(deleteConvSql, conversationId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在");
            }
            
            // 删除 Redis 中的对话队列（与 sendMessage 使用一致的 key 前缀）
            redisTemplate.delete("chatmemory:" + conversationId);
            
            return ResultUtils.success(true);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除对话失败：" + e.getMessage());
        }
    }

    /**
     * P22 增强：更新对话标题
     */
    @PutMapping("/{conversationId}")
    public BaseResponse<Map<String, Object>> updateConversation(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request) {
        
        String title = request.get("title");
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
        }
        
        try {
            String sql = "UPDATE t_conversation SET title = ?, updated_at = NOW() WHERE id = ?";
            int rows = jdbcTemplate.update(sql, title.trim(), conversationId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对话不存在");
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("title", title.trim());
            result.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return ResultUtils.success(result);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新对话失败：" + e.getMessage());
        }
    }

    // ==================== P6 修复：对话接口实现 ====================

    /**
     * P6 修复：发送消息接口
     */
    @PostMapping("/{conversationId}/message")
    public BaseResponse<Map<String, Object>> sendMessage(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> request) {
        
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }
        
        // 创建消息对象
        Map<String, Object> message = new LinkedHashMap<>();
        String messageId = UUID.randomUUID().toString();
        message.put("id", messageId);
        message.put("msgId", messageId);
        message.put("conversationId", conversationId);
        message.put("content", content.trim());
        message.put("role", "user");
        message.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        try {
            // 保存消息到 Redis 队列（使用统一的 key 前缀）
            String messageJson = new ObjectMapper().writeValueAsString(message);
            redisTemplate.opsForList().rightPush("chatmemory:" + conversationId, messageJson);

            // 同时保存到数据库
            String sql = "INSERT INTO t_chat_message_raw (id, msg_id, conversation_id, content, role, create_time) VALUES (?, ?, ?, ?, ?, NOW())";
            jdbcTemplate.update(sql, System.currentTimeMillis(), messageId, conversationId, content, "user");

            // 更新对话的 updated_at
            String updateSql = "UPDATE t_conversation SET updated_at = NOW() WHERE id = ?";
            jdbcTemplate.update(updateSql, conversationId);

            return ResultUtils.success(message);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息保存失败：" + e.getMessage());
        }
    }

    /**
     * P6 修复：获取消息历史接口（分页）
     */
    @GetMapping("/{conversationId}/messages")
    public BaseResponse<Map<String, Object>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        
        try {
            // 从数据库查询消息（按时间排序）
            String sql = "SELECT id, conversation_id, content, role, create_time FROM t_chat_message_raw " +
                        "WHERE conversation_id = ? ORDER BY create_time ASC LIMIT ? OFFSET ?";
            
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                sql, 
                conversationId,
                pageSize,
                (page - 1) * pageSize
            );
            
            // 获取总数
            String countSql = "SELECT COUNT(*) as total FROM t_chat_message_raw WHERE conversation_id = ?";
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, conversationId);
            if (total == null) total = 0;
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("list", messages);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("pages", (total + pageSize - 1) / pageSize);
            
            return ResultUtils.success(result);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取消息失败：" + e.getMessage());
        }
    }

    /**
     * P6 修复：删除消息接口
     */
    @DeleteMapping("/{conversationId}/message/{messageId}")
    public BaseResponse<Boolean> deleteMessage(
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        
        try {
            String sql = "DELETE FROM t_chat_message_raw WHERE conversation_id = ? AND id = ?";
            int rows = jdbcTemplate.update(sql, conversationId, messageId);
            
            if (rows == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "消息不存在");
            }
            
            return ResultUtils.success(true);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除消息失败：" + e.getMessage());
        }
    }

    /**
     * P6 修复：创建新对话接口
     */
    @PostMapping
    public BaseResponse<Map<String, Object>> createConversation(
            @RequestBody(required = false) Map<String, String> request) {
        
        String title = request != null ? request.get("title") : null;
        if (title == null || title.trim().isEmpty()) {
            title = "新对话-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        
        String conversationId = UUID.randomUUID().toString();
        
        try {
            // 保存对话记录到数据库
            String sql = "INSERT INTO t_conversation (id, title, created_at, updated_at) VALUES (?, ?, NOW(), NOW())";
            jdbcTemplate.update(sql, conversationId, title);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("title", title);
            result.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return ResultUtils.success(result);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建对话失败：" + e.getMessage());
        }
    }
}
