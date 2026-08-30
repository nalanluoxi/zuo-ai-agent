package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class DashboardOverviewVO {
private long totalCalls; private double successRate; private double avgDurationMs; private double p95DurationMs;
}
