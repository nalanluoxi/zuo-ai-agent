package com.example.zuoaiagent.monitor.service;

import java.util.Map;

/**
 * P25：Redis 监控服务接口
 */
public interface RedisMonitorService {

    /**
     * 获取 QPS 趋势
     */
    Map<String, Object> getQpsTrend(int days);

    /**
     * 获取内存趋势
     */
    Map<String, Object> getMemoryTrend(int days);

    /**
     * 获取 Key 分桶统计
     */
    Map<String, Object> getBuckets(int page, int size);

    /**
     * 获取热 Key Top 20
     */
    Map<String, Object> getHotKeys();

    /**
     * 获取 Key 详情
     */
    Map<String, Object> getKeyDetail(String key);

    /**
     * 获取 BigKey 列表
     */
    Map<String, Object> getBigKeys(int page, int size);

    /**
     * 获取 Redis 信息
     */
    Map<String, Object> getRedisInfo();
}
