package com.example.zuoaiagent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载器
 *
 * <p>从 classpath:prompts/ 目录加载 .st 模板文件，支持 {{变量}} 占位符替换，
 * 内置 ConcurrentHashMap 缓存，每个文件只读一次。
 */
@Service
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptTemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载模板文件原始内容（不渲染变量）。
     *
     * @param path classpath 相对路径，如 "prompts/rag-kb.st"
     * @return 模板文本
     */
    public String load(String path) {
        return cache.computeIfAbsent(path, this::readResource);
    }

    /**
     * 加载并渲染模板，将 {{key}} 替换为 slots 中对应的值。
     *
     * @param path  模板路径
     * @param slots 变量映射
     * @return 渲染后的完整文本
     */
    public String render(String path, Map<String, String> slots) {
        String template = load(path);
        return fillSlots(template, slots);
    }

    // -------------------- 私有方法 --------------------

    private String readResource(String path) {
        String location = path.startsWith("classpath:") ? path : "classpath:" + path;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt 模板文件不存在：" + path);
        }
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("[PromptTemplateLoader] 加载模板: {}, 长度: {} 字符", path, content.length());
            return content;
        } catch (IOException e) {
            log.error("[PromptTemplateLoader] 读取模板失败: {}", path, e);
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
