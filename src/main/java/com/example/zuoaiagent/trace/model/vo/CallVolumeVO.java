package com.example.zuoaiagent.trace.model.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class CallVolumeVO {
private LocalDateTime hour; private long count;
}
