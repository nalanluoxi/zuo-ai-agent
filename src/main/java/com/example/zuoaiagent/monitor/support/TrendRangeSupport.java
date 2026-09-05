package com.example.zuoaiagent.monitor.support;

import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 监控趋势时间范围支持类
 * 统一处理 period（day/week/month/custom）→ 时间范围 + 时间桶序列 的换算，
 * 以及查询结果的缺桶补零。
 *
 * 语义与个人看板对齐：
 * - day    ：当天，按小时聚合，24 个点
 * - week   ：近 7 天（含今天），按天聚合
 * - month  ：当月 1 日到月底，按天聚合
 * - custom ：startDate ~ endDate（均含），按天聚合
 */
@Component
public class TrendRangeSupport {

    /** 小时粒度时间桶格式（to_char 用） */
    public static final String BUCKET_FORMAT_HOUR = "YYYY-MM-DD HH24:00";
    /** 天粒度时间桶格式（to_char 用） */
    public static final String BUCKET_FORMAT_DAY = "YYYY-MM-DD";

    private static final DateTimeFormatter HOUR_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 自定义范围最大天数，防止滥用 */
    private static final int MAX_CUSTOM_DAYS = 92;

    /**
     * 趋势时间范围
     *
     * @param period       归一化后的周期：day / week / month / custom
     * @param bucketFormat SQL to_char 使用的时间桶格式
     * @param startTime    查询起始（含）
     * @param endTime      查询结束（不含）
     * @param buckets      有序时间桶标签列表
     */
    public record TrendRange(String period, String bucketFormat, Timestamp startTime, Timestamp endTime,
                             List<String> buckets) {
    }

    /**
     * 兼容旧版 days 参数（仅支持 7/30）：period 为空时按 days 映射
     */
    public static String normalizePeriod(String period, Integer days) {
        if (period != null && !period.isBlank()) {
            return switch (period) {
                case "7" -> "week";
                case "30" -> "month";
                default -> period;
            };
        }
        if (days != null) {
            if (days == 7) return "week";
            if (days == 30) return "month";
        }
        return "week";
    }

    /**
     * 解析时间范围，生成时间桶序列
     */
    public TrendRange resolve(String period, String startDate, String endDate) {
        String normalized = normalizePeriod(period, null);
        LocalDate today = LocalDate.now();
        switch (normalized) {
            case "day" -> {
                LocalDateTime start = today.atStartOfDay();
                List<String> buckets = new ArrayList<>(24);
                for (int h = 0; h < 24; h++) {
                    buckets.add(start.plusHours(h).format(HOUR_LABEL_FORMAT));
                }
                return new TrendRange("day", BUCKET_FORMAT_HOUR,
                        Timestamp.valueOf(start), Timestamp.valueOf(start.plusDays(1)), buckets);
            }
            case "month" -> {
                LocalDate first = today.withDayOfMonth(1);
                LocalDate nextMonthFirst = first.plusMonths(1);
                List<String> buckets = new ArrayList<>();
                for (LocalDate d = first; d.isBefore(nextMonthFirst); d = d.plusDays(1)) {
                    buckets.add(d.format(DAY_LABEL_FORMAT));
                }
                return new TrendRange("month", BUCKET_FORMAT_DAY,
                        Timestamp.valueOf(first.atStartOfDay()), Timestamp.valueOf(nextMonthFirst.atStartOfDay()), buckets);
            }
            case "custom" -> {
                if (startDate == null || endDate == null || startDate.isBlank() || endDate.isBlank()) {
                    // 自定义但未选日期：退化为近 7 天
                    return resolve("week", null, null);
                }
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                if (end.isBefore(start)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
                if (start.isBefore(end.minusDays(MAX_CUSTOM_DAYS))) {
                    start = end.minusDays(MAX_CUSTOM_DAYS);
                }
                List<String> buckets = new ArrayList<>();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    buckets.add(d.format(DAY_LABEL_FORMAT));
                }
                return new TrendRange("custom", BUCKET_FORMAT_DAY,
                        Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.plusDays(1).atStartOfDay()), buckets);
            }
            default -> {
                // week：近 7 天（含今天）
                LocalDate start = today.minusDays(6);
                List<String> buckets = new ArrayList<>(7);
                for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                    buckets.add(d.format(DAY_LABEL_FORMAT));
                }
                return new TrendRange("week", BUCKET_FORMAT_DAY,
                        Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(today.plusDays(1).atStartOfDay()), buckets);
            }
        }
    }

    /**
     * 按时间桶序列补齐缺失数据点（补零）
     *
     * @param buckets     有序时间桶标签
     * @param byBucket    查询结果：bucket 标签 → 指标值
     * @param zeroFactory 缺失桶的零值模板
     * @return 有序趋势数据，每项都含 date 字段
     */
    public List<Map<String, Object>> fillBuckets(List<String> buckets,
                                                 Map<String, Map<String, Object>> byBucket,
                                                 Supplier<Map<String, Object>> zeroFactory) {
        List<Map<String, Object>> result = new ArrayList<>(buckets.size());
        for (String bucket : buckets) {
            Map<String, Object> values = byBucket.get(bucket);
            if (values == null) {
                values = zeroFactory.get();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", bucket);
            item.putAll(values);
            result.add(item);
        }
        return result;
    }
}
