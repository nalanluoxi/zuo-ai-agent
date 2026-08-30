package com.example.zuoaiagent.knowledge.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * P23：知识库文件管理接口
 * 功能：上传文件、MD5 重复检测、分页查询、删除、状态跟踪
 */
@RestController
@RequestMapping("/knowledge-base/{knowledgeBaseId}/files")
@RequiredArgsConstructor
public class KnowledgeFileController {

    private final JdbcTemplate jdbcTemplate;
    
    private static final String UPLOAD_DIR = "/tmp/knowledge-files/";
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * P23：上传文件（支持 MD5 重复检测）
     */
    @PostMapping("/upload")
    public BaseResponse<Map<String, Object>> uploadFile(
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) throws Exception {
        
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID无效");
        }
        
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小超过 100MB 限制");
        }
        
        try {
            // 计算文件 MD5
            String md5 = calculateMd5(file);
            
            // 检查 MD5 重复（同一知识库内）
            String checkSql = "SELECT id FROM t_knowledge_file WHERE knowledge_base_id = ? AND md5 = ?";
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, knowledgeBaseId, md5);
            
            if (!existing.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    "文件已存在（MD5: " + md5 + "），无需重复上传");
            }
            
            // 保存文件
            String uploadDir = UPLOAD_DIR;
            Files.createDirectories(Paths.get(uploadDir));
            
            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, fileName);
            file.transferTo(filePath);
            
            // 插入数据库记录
            String fileId = UUID.randomUUID().toString();
            String sql = "INSERT INTO t_knowledge_file (id, knowledge_base_id, filename, original_name, md5, file_path, file_size, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
            
            jdbcTemplate.update(sql, 
                fileId, 
                knowledgeBaseId, 
                fileName, 
                file.getOriginalFilename(),
                md5, 
                filePath.toString(), 
                file.getSize(),
                "QUEUED"  // 初始状态：待处理
            );
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", fileId);
            result.put("fileName", file.getOriginalFilename());
            result.put("fileSize", file.getSize());
            result.put("md5", md5);
            result.put("status", "QUEUED");
            result.put("uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return ResultUtils.success(result);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * P23：分页查询知识库文件列表
     */
    @GetMapping
    public BaseResponse<Map<String, Object>> listFiles(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        try {
            // 构建查询 SQL
            StringBuilder sql = new StringBuilder(
                "SELECT id, knowledge_base_id, filename, original_name, md5, file_size, status, chunks, created_at, updated_at " +
                "FROM t_knowledge_file WHERE knowledge_base_id = ?"
            );
            List<Object> params = new ArrayList<>();
            params.add(knowledgeBaseId);
            
            // 按状态过滤
            if (status != null && !status.trim().isEmpty()) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            
            // 排序和分页
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((page - 1) * size);
            
            // 查询数据
            List<Map<String, Object>> content = jdbcTemplate.queryForList(
                sql.toString(),
                params.toArray()
            );
            
            // 获取总数
            StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) as total FROM t_knowledge_file WHERE knowledge_base_id = ?"
            );
            List<Object> countParams = new ArrayList<>();
            countParams.add(knowledgeBaseId);
            
            if (status != null && !status.trim().isEmpty()) {
                countSql.append(" AND status = ?");
                countParams.add(status);
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取文件列表失败：" + e.getMessage());
        }
    }

    /**
     * P23：删除知识库文件
     */
    @DeleteMapping("/{fileId}")
    public BaseResponse<Boolean> deleteFile(
            @PathVariable Long knowledgeBaseId,
            @PathVariable String fileId) {
        
        try {
            // 查询文件信息
            String querySql = "SELECT file_path FROM t_knowledge_file WHERE id = ? AND knowledge_base_id = ?";
            List<Map<String, Object>> files = jdbcTemplate.queryForList(querySql, fileId, knowledgeBaseId);
            
            if (files.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
            }
            
            String filePath = (String) files.get(0).get("file_path");
            
            // 删除物理文件
            if (filePath != null && !filePath.isEmpty()) {
                try {
                    Files.deleteIfExists(Paths.get(filePath));
                } catch (Exception ignored) {
                    // 文件删除失败不影响数据库记录删除
                }
            }
            
            // 删除数据库记录
            String deleteSql = "DELETE FROM t_knowledge_file WHERE id = ? AND knowledge_base_id = ?";
            jdbcTemplate.update(deleteSql, fileId, knowledgeBaseId);
            
            return ResultUtils.success(true);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除文件失败：" + e.getMessage());
        }
    }

    /**
     * P23：更新文件状态（QUEUED → CHUNKING → VECTORIZING → DONE/FAILED）
     */
    @PutMapping("/{fileId}/status")
    public BaseResponse<Map<String, Object>> updateFileStatus(
            @PathVariable Long knowledgeBaseId,
            @PathVariable String fileId,
            @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        String errorMsg = request.get("errorMessage");
        String chunks = request.get("chunks");
        
        if (status == null || status.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不能为空");
        }
        
        try {
            // 验证文件是否存在
            String checkSql = "SELECT id FROM t_knowledge_file WHERE id = ? AND knowledge_base_id = ?";
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, fileId, knowledgeBaseId);
            
            if (existing.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
            }
            
            // 更新状态
            StringBuilder updateSql = new StringBuilder(
                "UPDATE t_knowledge_file SET status = ?, updated_at = NOW()"
            );
            List<Object> params = new ArrayList<>();
            params.add(status);
            
            if (errorMsg != null && !errorMsg.isEmpty()) {
                updateSql.append(", error_message = ?");
                params.add(errorMsg);
            }
            
            if (chunks != null && !chunks.isEmpty()) {
                updateSql.append(", chunks = ?");
                params.add(chunks);
            }
            
            updateSql.append(" WHERE id = ? AND knowledge_base_id = ?");
            params.add(fileId);
            params.add(knowledgeBaseId);
            
            jdbcTemplate.update(updateSql.toString(), params.toArray());
            
            // 查询更新后的数据
            String selectSql = "SELECT id, knowledge_base_id, filename, original_name, md5, file_size, status, chunks, created_at, updated_at " +
                              "FROM t_knowledge_file WHERE id = ? AND knowledge_base_id = ?";
            List<Map<String, Object>> files = jdbcTemplate.queryForList(selectSql, fileId, knowledgeBaseId);
            
            Map<String, Object> result = files.isEmpty() ? new LinkedHashMap<>() : files.get(0);
            return ResultUtils.success(result);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新文件状态失败：" + e.getMessage());
        }
    }

    /**
     * P23：获取文件详情
     */
    @GetMapping("/{fileId}")
    public BaseResponse<Map<String, Object>> getFileDetail(
            @PathVariable Long knowledgeBaseId,
            @PathVariable String fileId) {
        
        try {
            String sql = "SELECT id, knowledge_base_id, filename, original_name, md5, file_size, status, chunks, error_message, created_at, updated_at " +
                        "FROM t_knowledge_file WHERE id = ? AND knowledge_base_id = ?";
            
            List<Map<String, Object>> files = jdbcTemplate.queryForList(sql, fileId, knowledgeBaseId);
            
            if (files.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
            }
            
            return ResultUtils.success(files.get(0));
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取文件详情失败：" + e.getMessage());
        }
    }

    /**
     * 计算文件 MD5 哈希值
     */
    private String calculateMd5(MultipartFile file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
