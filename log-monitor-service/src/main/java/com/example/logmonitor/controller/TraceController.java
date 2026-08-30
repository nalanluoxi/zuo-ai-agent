package com.example.logmonitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.TraceSpanDO;
import com.example.logmonitor.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/traces")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/search")
    public Page<TraceSpanDO> search(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size) {
        return traceService.search(serviceName, status, startTime, endTime, current, size);
    }

    @GetMapping("/{traceId}")
    public List<TraceSpanDO> getTrace(@PathVariable String traceId) {
        return traceService.getTrace(traceId);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "24") int hours) {
        return traceService.getOverview(hours);
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend(@RequestParam(defaultValue = "24") int hours) {
        return traceService.getTrend(hours);
    }
}
