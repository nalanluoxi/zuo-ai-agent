package com.example.zuoaiagent.monitor.service;

import java.util.Map;

/**
 * P26：数据库监控服务接口
 */
public interface DbMonitorService {

    /**
     * 获取 QPS 趋势
     */
    Map<String, Object> getQpsTrend(int days);

    /**
     * 获取访问流量趋势
     */
    Map<String, Object> getAccessTrend(int days);

    /**
     * 获取表空间趋势
     */
    Map<String, Object> getTablespaceTrend(int days);

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
