package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class ErrorTrendVO {
private LocalDateTime hour; private long total; private long errors;
}
