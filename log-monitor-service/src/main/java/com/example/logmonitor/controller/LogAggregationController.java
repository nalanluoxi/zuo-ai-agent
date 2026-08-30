package com.example.logmonitor.controller;

import com.example.logmonitor.service.LogAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aggregation")
@RequiredArgsConstructor
public class LogAggregationController {

    private final LogAggregationService logAggregationService;

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "hour") String granularity) {
        return logAggregationService.search(keyword, serviceName, startTime, endTime, granularity);
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend(
            @RequestParam String token,
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "24") int hours) {
        return logAggregationService.getTokenTrend(token, serviceName, hours);
    }
}
