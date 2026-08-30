package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class NodeDurationVO {
private String nodeType; private long count; private double avgDurationMs; private double p95DurationMs;
}
