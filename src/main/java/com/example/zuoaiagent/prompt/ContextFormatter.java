package com.example.zuoaiagent.prompt;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索文档格式化器
 *
 * <p>将 {@link Document} 列表格式化为结构化的知识库上下文文本，
 * 供注入 LLM 系统提示词使用。
 *
 * <p>格式示例：
 * <pre>
 * #### 知识库片段
 * ````text
 * [1] 第一段文档内容...
 *
 * [2] 第二段文档内容...
 * ````
 * </pre>
 */
@Service
public class ContextFormatter {

    /**
     * 将文档列表格式化为知识库上下文块。
     *
     * @param docs 检索并重排序后的文档列表
     * @return 格式化后的上下文文本；列表为空时返回空字符串
     */
    public String format(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#### 知识库片段\n````text\n");

        for (int i = 0; i < docs.size(); i++) {
            String text = docs.get(i).getFormattedContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            sb.append("[").append(i + 1).append("] ").append(text.trim());
            if (i < docs.size() - 1) {
                sb.append("\n\n");
            }
        }

        sb.append("\n````");
        return sb.toString();
    }
}
