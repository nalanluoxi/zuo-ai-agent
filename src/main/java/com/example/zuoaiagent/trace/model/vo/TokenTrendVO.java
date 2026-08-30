package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class TokenTrendVO {
private LocalDateTime hour; private long promptTokens; private long completionTokens;
}
