package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.raglab.entity.DataReplayRequestDO;
import com.example.zuoaiagent.raglab.service.DataReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生产数据回放控制器
 */
@RestController
@RequestMapping("/rag-lab/data-replay")
@Tag(name = "生产数据回放", description = "生产数据回放审批 API")
public class DataReplayController {

    private final DataReplayService replayService;

    public DataReplayController(DataReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping
    @Operation(summary = "提交回放申请")
    public DataReplayRequestDO submitRequest(@RequestBody Map<String, Object> body) {
        String traceId = (String) body.get("traceId");
        String questionText = (String) body.get("questionText");
        String sourceConversationId = (String) body.get("sourceConversationId");
        Long createUserId = body.get("createUserId") != null ? Long.valueOf(body.get("createUserId").toString()) : null;
        return replayService.submitRequest(traceId, questionText, sourceConversationId, createUserId);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有回放申请")
    public List<DataReplayRequestDO> listAll() {
        return replayService.listAll();
    }

    @GetMapping("/list/{status}")
    @Operation(summary = "按状态查询回放申请")
    public List<DataReplayRequestDO> listByStatus(@PathVariable String status) {
        return replayService.listByStatus(status);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过")
    public DataReplayRequestDO approve(@PathVariable Long id, @RequestParam Long approvedBy) {
        return replayService.approve(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "审批拒绝")
    public DataReplayRequestDO reject(@PathVariable Long id, @RequestParam Long approvedBy) {
        return replayService.reject(id, approvedBy);
    }
}
