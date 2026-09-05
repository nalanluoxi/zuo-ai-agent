package com.example.zuoaiagent.monitor.service;

import java.util.Map;

/**
 * P26：数据库监控服务接口
 */
public interface DbMonitorService {

    /**
     * 获取 QPS 趋势（事务数增量）
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getQpsTrend(String period, String startDate, String endDate);

    /**
     * 获取访问流量趋势（读/写增量）
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getAccessTrend(String period, String startDate, String endDate);

    /**
     * 获取表空间趋势
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     */
    Map<String, Object> getTablespaceTrend(String period, String startDate, String endDate);

    /**
     * 获取慢查询列表
     */
    Map<String, Object> getSlowQueries(int page, int size, String dbName);

    /**
     * 获取表 Schema
     */
    Map<String, Object> getTableSchema(String dbName, String tableName);

    /**
     * 获取表数据
     */
    Map<String, Object> getTableData(String dbName, String tableName, int page, int size, String whereClause);

    /**
     * 获取数据库信息
     */
    Map<String, Object> getDbInfo();
}
