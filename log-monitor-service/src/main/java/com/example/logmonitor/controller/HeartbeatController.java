package com.example.logmonitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.HeartbeatDO;
import com.example.logmonitor.service.HeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    @GetMapping("/search")
    public Page<HeartbeatDO> search(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size) {
        return heartbeatService.search(serviceName, host, startTime, endTime, current, size);
    }

    @GetMapping("/status")
    public Map<String, Object> statusBoard() {
        return heartbeatService.getStatusBoard();
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "24") int hours) {
        return heartbeatService.getTrend(serviceName, hours);
    }
}
