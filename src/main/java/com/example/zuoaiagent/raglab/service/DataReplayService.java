package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.raglab.entity.DataReplayRequestDO;

import java.util.List;

/**
 * 生产数据回放服务
 */
public interface DataReplayService {

    /**
     * 提交回放申请（从链路回捞提问）
     */
    DataReplayRequestDO submitRequest(String traceId, String questionText,
                                      String sourceConversationId, Long createUserId);

    /**
     * 查询所有回放申请
     */
    List<DataReplayRequestDO> listAll();

    /**
     * 按状态查询
     */
    List<DataReplayRequestDO> listByStatus(String status);

    /**
     * 审批通过 → 自动写入 t_rag_test_question（source=production）
     */
    DataReplayRequestDO approve(Long requestId, Long approvedBy);

    /**
     * 审批拒绝
     */
    DataReplayRequestDO reject(Long requestId, Long approvedBy);
}
