package com.example.zuoaiagent.admin.service;

import java.util.Map;

/**
 * P29：链路追踪服务接口
 */
public interface RagTraceService {

    /**
     * 获取完整链路
     */
    Map<String, Object> getCompleteTrace(String inputId);

    /**
     * 获取链路统计
     */
    Map<String, Object> getTraceStats();
}
