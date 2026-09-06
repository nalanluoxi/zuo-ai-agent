package com.example.zuoaiagent.raglab.interceptor;

/**
 * RAG 灰度标签持有器（ThreadLocal）
 *
 * <p>由 {@link RagGrayInterceptor} 在请求进入时设置，
 * 由 {@link com.example.zuoaiagent.pipeline.SmartRagPipeline} 在执行时读取。
 * 请求结束后自动清除。
 */
public class GrayContextHolder {

    private static final ThreadLocal<String> GRAY_TAG = new ThreadLocal<>();

    public static void set(String tag) {
        GRAY_TAG.set(tag);
    }

    public static String get() {
        return GRAY_TAG.get();
    }

    public static void clear() {
        GRAY_TAG.remove();
    }
}
