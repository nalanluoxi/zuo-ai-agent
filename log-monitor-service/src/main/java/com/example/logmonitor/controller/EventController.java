package com.example.logmonitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.logmonitor.entity.EventDO;
import com.example.logmonitor.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/event")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/search")
    public Page<EventDO> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size) {
        return eventService.search(eventType, serviceName, startTime, endTime, current, size);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "24") int hours) {
        return eventService.getOverview(hours);
    }

    @GetMapping("/trend")
    public List<Map<String, Object>> trend(@RequestParam(defaultValue = "24") int hours) {
        return eventService.getTrend(hours);
    }
}
