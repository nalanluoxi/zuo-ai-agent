package com.example.zuoaiagent.log;

import java.lang.annotation.*;

/**
 * CAT 风格事务日志注解
 *
 * <p>标注在方法上，AOP 切面会自动记录 transaction 类型日志，
 * 包含方法入参、返回值、耗时等信息。
 *
 * <p>使用示例：
 * <pre>
 * {@code @LogTransaction(name = "RAG查询改写", eventType = "REWRITE")}
 * public String rewrite(String query) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogTransaction {

    /** 事务名称 */
    String name() default "";

    /** 事件类型 */
    String eventType() default "";

    /** 是否记录入参（默认 true） */
    boolean logInput() default true;

    /** 是否记录出参（默认 true） */
    boolean logOutput() default true;

    /** 入参最大长度（截断） */
    int maxInputLength() default 500;

    /** 出参最大长度（截断） */
    int maxOutputLength() default 500;
}
