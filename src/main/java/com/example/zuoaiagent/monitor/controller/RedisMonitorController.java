package com.example.zuoaiagent.monitor.controller;

import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.exception.BusinessException;
import com.example.zuoaiagent.exception.ErrorCode;
import com.example.zuoaiagent.monitor.service.RedisMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * P25：Redis 监控 API
 * 功能：QPS 趋势、内存趋势、热 Key、BigKey、分桶统计
 */
@RestController
@RequestMapping("/monitor/redis")
@RequiredArgsConstructor
public class RedisMonitorController {

    private final RedisMonitorService redisMonitorService;

    /**
     * P25：获取 Redis QPS 趋势
     * @param days 统计天数（7/30）
     * @return QPS 每日数据
     */
    @GetMapping("/qps-trend")
    public BaseResponse<Map<String, Object>> getQpsTrend(
            @RequestParam(defaultValue = "7") int days) {
        
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "天数只能选择 7 或 30");
        }
        
        return ResultUtils.success(redisMonitorService.getQpsTrend(days));
    }

    /**
     * P25：获取 Redis 内存趋势
     * @param days 统计天数（7/30）
     * @return 内存使用数据
     */
    @GetMapping("/memory-trend")
    public BaseResponse<Map<String, Object>> getMemoryTrend(
            @RequestParam(defaultValue = "7") int days) {
        
        if (days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "天数只能选择 7 或 30");
        }
        
        return ResultUtils.success(redisMonitorService.getMemoryTrend(days));
    }

    /**
     * P25：获取 Key 分桶统计
     * @param page 分页页码
     * @param size 每页大小
     * @return 按 Key 模式分组的统计数据
     */
    @GetMapping("/buckets")
    public BaseResponse<Map<String, Object>> getBuckets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(redisMonitorService.getBuckets(page, size));
    }

    /**
     * P25：获取热 Key Top 20
     * @return 访问次数最多的 Key 列表
     */
    @GetMapping("/keys")
    public BaseResponse<Map<String, Object>> getHotKeys() {
        return ResultUtils.success(redisMonitorService.getHotKeys());
    }

    /**
     * P25：获取单个 Key 的详细信息
     * @param key Redis Key
     * @return Key 的类型、大小、访问次数、过期时间等
     */
    @GetMapping("/key/{key}")
    public BaseResponse<Map<String, Object>> getKeyDetail(@PathVariable String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Key 不能为空");
        }
        
        return ResultUtils.success(redisMonitorService.getKeyDetail(key));
    }

    /**
     * P25：获取 BigKey 列表
     * @param page 分页页码
     * @param size 每页大小
     * @return 超过阈值的大 Key
     */
    @GetMapping("/bigkeys")
    public BaseResponse<Map<String, Object>> getBigKeys(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        
        return ResultUtils.success(redisMonitorService.getBigKeys(page, size));
    }

    /**
     * P25：获取 Redis 总体信息
     * @return 连接数、内存、Key 总数等
     */
    @GetMapping("/info")
    public BaseResponse<Map<String, Object>> getRedisInfo() {
        return ResultUtils.success(redisMonitorService.getRedisInfo());
    }
}
