package com.example.zuoaiagent.monitor.trace;

import lombok.Data;

/**
 * 当前线程的 trace 上下文（ThreadLocal 绑定）
 */
@Data
public class TraceContext {
    private static final ThreadLocal<TraceContext> HOLDER = ThreadLocal.withInitial(TraceContext::new);

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String operationName;
    private long startTimeMs;

    public static TraceContext current() { return HOLDER.get(); }
    public static void set(TraceContext ctx) { HOLDER.set(ctx); }
    public static void clear() { HOLDER.remove(); }
}
