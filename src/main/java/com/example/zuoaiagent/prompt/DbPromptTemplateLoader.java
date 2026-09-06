package com.example.zuoaiagent.prompt;

import com.example.zuoaiagent.raglab.service.RagPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库优先的 Prompt 模板加载器
 *
 * <p>优先从数据库 {@link RagPromptService} 读取模板内容，
 * 数据库无记录时 fallback 到 classpath:prompts/ 下的 .st 文件。
 *
 * <p>提示词类型与模板路径的映射关系：
 * <ul>
 *   <li>QUERY_REWRITE → prompts/query-rewrite.st</li>
 *   <li>RERANK_SCORE → prompts/document-score.st</li>
 *   <li>INTENT_CLASSIFY → prompts/intent-classify.st</li>
 *   <li>SYSTEM_CHAT → prompts/system-chat.st</li>
 *   <li>HYDE_DOC → (无文件对应，仅 DB)</li>
 *   <li>HYDE_EQUIV → (无文件对应，仅 DB)</li>
 * </ul>
 */
@Service
public class DbPromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(DbPromptTemplateLoader.class);

    /** 提示词类型 → 文件路径映射 */
    private static final Map<String, String> TYPE_TO_PATH = Map.of(
            "QUERY_REWRITE", "prompts/query-rewrite.st",
            "RERANK_SCORE", "prompts/document-score.st",
            "INTENT_CLASSIFY", "prompts/intent-classify.st",
            "SYSTEM_CHAT", "prompts/system-chat.st"
    );

    private final RagPromptService ragPromptService;
    private final ResourceLoader resourceLoader;
    private final Map<String, String> fileCache = new ConcurrentHashMap<>();

    public DbPromptTemplateLoader(RagPromptService ragPromptService,
                                  ResourceLoader resourceLoader) {
        this.ragPromptService = ragPromptService;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载模板内容（DB 优先，文件兜底）。
     *
     * @param promptType 提示词类型（如 QUERY_REWRITE）
     * @return 模板文本
     */
    public String load(String promptType) {
        // 1. 优先从 DB 读取
        try {
            return ragPromptService.getTemplateContent(promptType);
        } catch (Exception e) {
            log.debug("[DbPromptTemplateLoader] DB 中无模板 type={}, 尝试文件兜底: {}", promptType, e.getMessage());
        }

        // 2. Fallback 到文件
        String path = TYPE_TO_PATH.get(promptType);
        if (path == null) {
            throw new IllegalStateException("未知提示词类型: " + promptType);
        }
        return loadFromFile(path);
    }

    /**
     * 加载并渲染模板。
     *
     * @param promptType 提示词类型
     * @param slots      变量映射
     * @return 渲染后的文本
     */
    public String render(String promptType, Map<String, String> slots) {
        String template = load(promptType);
        return fillSlots(template, slots);
    }

    /**
     * 兼容旧版 PromptTemplateLoader 的接口。
     *
     * @param path  classpath 相对路径，如 "prompts/rag-kb.st"
     * @return 模板文本
     */
    public String loadByPath(String path) {
        return loadFromFile(path);
    }

    /**
     * 兼容旧版 PromptTemplateLoader 的渲染接口。
     */
    public String renderByPath(String path, Map<String, String> slots) {
        String template = loadByPath(path);
        return fillSlots(template, slots);
    }

    /**
     * 提示词类型 → 模板路径（用于外部组件调用）。
     */
    public String getPathByType(String promptType) {
        return TYPE_TO_PATH.get(promptType);
    }

    // -------------------- 私有方法 --------------------

    private String loadFromFile(String path) {
        return fileCache.computeIfAbsent(path, p -> readResource(p));
    }

    private String readResource(String path) {
        String location = path.startsWith("classpath:") ? path : "classpath:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt 模板文件不存在：" + path);
        }
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("[DbPromptTemplateLoader] 加载文件模板: {}, 长度: {} 字符", path, content.length());
            return content;
        } catch (IOException e) {
            log.error("[DbPromptTemplateLoader] 读取模板文件失败: {}", path, e);
            throw new IllegalStateException("读取 Prompt 模板失败：" + path, e);
        }
    }

    private String fillSlots(String template, Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
