package com.example.zuoaiagent.monitor.service;

import java.util.Map;

/**
 * P25：Redis 监控服务接口
 */
public interface RedisMonitorService {

    /**
     * 获取 QPS 趋势
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getQpsTrend(String period, String startDate, String endDate);

    /**
     * 获取内存趋势
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getMemoryTrend(String period, String startDate, String endDate);

    /**
     * 获取延迟趋势（数据来源 t_redis_latency，每分钟采集）
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getLatencyTrend(String period, String startDate, String endDate);

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
