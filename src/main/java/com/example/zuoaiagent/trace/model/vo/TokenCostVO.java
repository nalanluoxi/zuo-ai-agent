package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TokenCostVO {
private long totalPromptTokens; private long totalCompletionTokens; private long callCount; private double estimatedCost;
}
