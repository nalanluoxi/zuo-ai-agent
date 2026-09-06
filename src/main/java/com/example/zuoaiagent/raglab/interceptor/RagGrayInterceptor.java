package com.example.zuoaiagent.raglab.interceptor;

import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import com.example.zuoaiagent.raglab.service.RagConfigLoader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * RAG 灰度路由拦截器
 *
 * <p>根据 {@code t_rag_config} 中的灰度配置，将请求分配到 BASELINE / TAG_A / TAG_B 三组。
 * 分流方式支持百分比随机 + 手动指定用户ID列表，或两者混合。
 *
 * <p>分流结果同时存入 request attribute "grayTag" 和 {@link GrayContextHolder}（ThreadLocal），
 * 后续由 SmartRagPipeline 读取。
 */
@Component
public class RagGrayInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RagGrayInterceptor.class);

    private final RagConfigLoader configLoader;

    public RagGrayInterceptor(RagConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RagConfigDO config = configLoader.getActiveConfig();

        if (config == null || config.getGrayEnabled() == null || config.getGrayEnabled() == 0) {
            String tag = "BASELINE";
            request.setAttribute("grayTag", tag);
            GrayContextHolder.set(tag);
            return true;
        }

        String grayMode = config.getGrayMode() != null ? config.getGrayMode() : "PERCENT";
        String userIdStr = request.getHeader("X-User-Id");

        String tag = determineGrayTag(userIdStr, config, grayMode);
        request.setAttribute("grayTag", tag);
        GrayContextHolder.set(tag);
        log.debug("[RagGrayInterceptor] userId={}, tag={}", userIdStr, tag);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        GrayContextHolder.clear();
    }

    /**
     * 根据分流模式决定灰度标签。
     */
    private String determineGrayTag(String userIdStr, RagConfigDO config, String grayMode) {
        switch (grayMode) {
            case "LIST":
                return determineByUserList(userIdStr, config);
            case "BOTH":
                return determineByBoth(userIdStr, config);
            case "PERCENT":
            default:
                return determineByPercent(userIdStr, config);
        }
    }

    /**
     * 百分比分流：按 userId hashCode 取模分配。
     */
    private String determineByPercent(String userIdStr, RagConfigDO config) {
        double ratio = config.getGrayRatio() != null ? config.getGrayRatio() : 0.1;
        int hash = userIdStr != null ? userIdStr.hashCode() : (int) (Math.random() * 10000);
        int bucket = Math.abs(hash % 100);
        int grayThreshold = (int) (ratio * 100);

        if (bucket < grayThreshold) {
            // 灰度用户：前一半走 TAG_A，后一半走 TAG_B
            return bucket < grayThreshold / 2 ? "TAG_A" : "TAG_B";
        }
        return "BASELINE";
    }

    /**
     * 手动用户列表分流。
     */
    private String determineByUserList(String userIdStr, RagConfigDO config) {
        if (userIdStr == null) return "BASELINE";
        List<String> userList = parseUserIds(config.getGrayUserIds());
        if (userList.contains(userIdStr)) {
            // 列表中的用户按 hashCode 分 A/B
            int hash = userIdStr.hashCode();
            return Math.abs(hash % 2) == 0 ? "TAG_A" : "TAG_B";
        }
        return "BASELINE";
    }

    /**
     * 混合模式：先查列表，再按比例。
     */
    private String determineByBoth(String userIdStr, RagConfigDO config) {
        if (userIdStr != null) {
            List<String> userList = parseUserIds(config.getGrayUserIds());
            if (userList.contains(userIdStr)) {
                int hash = userIdStr.hashCode();
                return Math.abs(hash % 2) == 0 ? "TAG_A" : "TAG_B";
            }
        }
        // 不在列表中的走百分比分流
        return determineByPercent(userIdStr, config);
    }

    /**
     * 解析用户 ID 列表（JSON 数组或逗号分隔）。
     */
    private List<String> parseUserIds(String userIdsStr) {
        if (userIdsStr == null || userIdsStr.isBlank()) return List.of();
        // 简单处理：去掉 [ ] " 空格，按逗号分隔
        String cleaned = userIdsStr.replaceAll("[\\[\\]\"\\s]", "");
        return List.of(cleaned.split(","));
    }
}
