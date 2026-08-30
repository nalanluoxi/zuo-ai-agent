package com.example.logmonitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.AppLogDO;
import com.example.logmonitor.service.LogCollectService;
import com.example.logmonitor.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogCollectService logCollectService;
    private final LogQueryService logQueryService;

    @PostMapping("/collect")
    public String collect(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batch = (List<Map<String, Object>>) body.get("logs");
        if (batch != null && !batch.isEmpty()) {
            logCollectService.collectBatch(batch);
        }
        return "ok";
    }

    @GetMapping("/search")
    public Page<AppLogDO> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size) {
        return logQueryService.search(keyword, service, level, startTime, endTime, traceId, current, size);
    }

    @GetMapping("/trace/{traceId}")
    public List<AppLogDO> trace(@PathVariable String traceId) {
        return logQueryService.traceByTraceId(traceId);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats(@RequestParam(defaultValue = "24") int hours) {
        return logQueryService.getLogStats(LocalDateTime.now().minusHours(hours));
    }
}