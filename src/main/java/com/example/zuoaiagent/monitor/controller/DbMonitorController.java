package com.example.zuoaiagent.monitor.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.monitor.service.DbMonitorService;
import com.example.zuoaiagent.monitor.support.TrendRangeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P26：数据库监控 API
 * 功能：QPS 趋势、慢查询、表空间、数据查询
 */
@RestController
@RequestMapping("/monitor/db")
@RequiredArgsConstructor
public class DbMonitorController {

    private final DbMonitorService dbMonitorService;

    /**
     * 获取数据库 QPS 趋势（事务数增量）
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)，兼容旧 days 参数
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     * @param days 旧版参数（7/30），period 为空时生效
     */
    @GetMapping("/qps-trend")
    public BaseResponse<Map<String, Object>> getQpsTrend(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {

        return ResultUtils.success(dbMonitorService.getQpsTrend(
                TrendRangeSupport.normalizePeriod(period, days), startDate, endDate));
    }

    /**
     * 获取数据库访问流量趋势（读/写增量）
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)，兼容旧 days 参数
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     * @param days 旧版参数（7/30），period 为空时生效
     */
    @GetMapping("/access-trend")
    public BaseResponse<Map<String, Object>> getAccessTrend(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {

        return ResultUtils.success(dbMonitorService.getAccessTrend(
                TrendRangeSupport.normalizePeriod(period, days), startDate, endDate));
    }

    /**
     * 获取表空间趋势
     * @param period day(当天按小时)/week(近7天)/month(当月)/custom(自定义范围)，兼容旧 days 参数
     * @param startDate custom 时必填，yyyy-MM-dd
     * @param endDate custom 时必填，yyyy-MM-dd
     * @param days 旧版参数（7/30），period 为空时生效
     */
    @GetMapping("/tablespace-trend")
    public BaseResponse<Map<String, Object>> getTablespaceTrend(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {

        return ResultUtils.success(dbMonitorService.getTablespaceTrend(
                TrendRangeSupport.normalizePeriod(period, days), startDate, endDate));
    }

    /**
     * P26：获取慢查询列表
     * @param page 分页页码
     * @param size 每页大小
     * @param dbName 数据库名（可选）
     */
    @GetMapping("/slow-queries")
    public BaseResponse<Map<String, Object>> getSlowQueries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String dbName) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(dbMonitorService.getSlowQueries(page, size, dbName));
    }

    /**
     * P26：获取表 Schema（结构）
     * @param dbName 数据库名
     * @param tableName 表名
     */
    @GetMapping("/databases/{dbName}/tables/{tableName}/schema")
    public BaseResponse<Map<String, Object>> getTableSchema(
            @PathVariable String dbName,
            @PathVariable String tableName) {
        
        if (dbName == null || dbName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据库名不能为空");
        }
        
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "表名不能为空");
        }
        
        return ResultUtils.success(dbMonitorService.getTableSchema(dbName, tableName));
    }

    /**
     * P26：查询表数据（支持多条件过滤）
     * @param dbName 数据库名
     * @param tableName 表名
     * @param page 分页页码
     * @param size 每页大小
     * @param whereClause 条件子句（可选，如 "id > 10"）
     */
    @GetMapping("/databases/{dbName}/tables/{tableName}/data")
    public BaseResponse<Map<String, Object>> getTableData(
            @PathVariable String dbName,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String whereClause) {
        
        if (dbName == null || dbName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据库名不能为空");
        }
        
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "表名不能为空");
        }
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(dbMonitorService.getTableData(dbName, tableName, page, size, whereClause));
    }

    /**
     * P26：获取数据库总体信息
     */
    @GetMapping("/info")
    public BaseResponse<Map<String, Object>> getDbInfo() {
        return ResultUtils.success(dbMonitorService.getDbInfo());
    }
}
