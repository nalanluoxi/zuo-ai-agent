package com.example.zuoaiagent.raglab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.DataReplayRequestDO;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import com.example.zuoaiagent.raglab.mapper.DataReplayRequestMapper;
import com.example.zuoaiagent.raglab.service.DataReplayService;
import com.example.zuoaiagent.raglab.service.RagTestQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 生产数据回放服务实现
 */
@Service
public class DataReplayServiceImpl implements DataReplayService {

    private static final Logger log = LoggerFactory.getLogger(DataReplayServiceImpl.class);

    private final DataReplayRequestMapper requestMapper;
    private final RagTestQuestionService testQuestionService;

    public DataReplayServiceImpl(DataReplayRequestMapper requestMapper,
                                 RagTestQuestionService testQuestionService) {
        this.requestMapper = requestMapper;
        this.testQuestionService = testQuestionService;
    }

    @Override
    public DataReplayRequestDO submitRequest(String traceId, String questionText,
                                             String sourceConversationId, Long createUserId) {
        // 脱敏处理：去除用户身份信息
        String sanitizedQuestion = sanitizeQuestion(questionText);

        DataReplayRequestDO request = new DataReplayRequestDO();
        request.setTraceId(traceId);
        request.setQuestionText(sanitizedQuestion);
        request.setSourceConversationId(sourceConversationId);
        request.setStatus("PENDING");
        request.setCreateUserId(createUserId);
        request.setCreateTime(new Date());
        requestMapper.insert(request);

        log.info("[数据回放] 提交申请: traceId={}, question={}", traceId, sanitizedQuestion);
        return request;
    }

    @Override
    public List<DataReplayRequestDO> listAll() {
        return requestMapper.selectList(
                new LambdaQueryWrapper<DataReplayRequestDO>()
                        .orderByDesc(DataReplayRequestDO::getCreateTime)
        );
    }

    @Override
    public List<DataReplayRequestDO> listByStatus(String status) {
        return requestMapper.selectList(
                new LambdaQueryWrapper<DataReplayRequestDO>()
                        .eq(DataReplayRequestDO::getStatus, status)
                        .orderByDesc(DataReplayRequestDO::getCreateTime)
        );
    }

    @Override
    public DataReplayRequestDO approve(Long requestId, Long approvedBy) {
        DataReplayRequestDO request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("回放申请不存在: " + requestId);
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("只有 PENDING 状态可以审批，当前状态: " + request.getStatus());
        }

        // 写入测试题库（source=production）
        RagTestQuestionDO question = new RagTestQuestionDO();
        question.setQuestionText(request.getQuestionText());
        question.setCategory("production_replay");
        question.setDifficulty("medium");
        question.setCreateUserId(approvedBy);
        question.setCreateTime(new Date());
        RagTestQuestionDO created = testQuestionService.createQuestion(question);

        // 更新申请状态
        request.setStatus("APPROVED");
        request.setApprovedBy(approvedBy);
        request.setApprovedTime(new Date());
        request.setTargetQuestionId(created.getId());
        requestMapper.updateById(request);

        log.info("[数据回放] 审批通过: requestId={}, targetQuestionId={}", requestId, created.getId());
        return request;
    }

    @Override
    public DataReplayRequestDO reject(Long requestId, Long approvedBy) {
        DataReplayRequestDO request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("回放申请不存在: " + requestId);
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("只有 PENDING 状态可以拒绝，当前状态: " + request.getStatus());
        }

        request.setStatus("REJECTED");
        request.setApprovedBy(approvedBy);
        request.setApprovedTime(new Date());
        requestMapper.updateById(request);

        log.info("[数据回放] 审批拒绝: requestId={}", requestId);
        return request;
    }

    /**
     * 脱敏处理：去除用户身份信息
     */
    private String sanitizeQuestion(String questionText) {
        if (questionText == null) return "";
        // 简单脱敏：去除可能的用户 ID、手机号等敏感信息
        // 后续可扩展为更复杂的脱敏规则
        return questionText
                .replaceAll("\\b\\d{11}\\b", "***")  // 手机号
                .replaceAll("\\b\\d{18}\\b", "***")  // 身份证号
                .replaceAll("(?i)userId[=:]\\s*\\d+", "userId=***")
                .trim();
    }
}
