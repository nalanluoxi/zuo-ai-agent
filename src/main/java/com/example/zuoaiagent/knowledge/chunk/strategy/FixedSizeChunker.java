package com.example.zuoaiagent.knowledge.chunk.strategy;

import com.example.zuoaiagent.knowledge.chunk.ChunkingMode;
import com.example.zuoaiagent.knowledge.chunk.ChunkingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块器
 *
 * <p>按 {@code chunkSize} 字符滑动窗口切分文本，相邻块保留 {@code overlapSize} 重叠，
 * 在边界处优先对齐到换行 > 中文句末标点 > 英文句末标点，减少语义割裂。
 *
 * <p>主要增强点（参照 ragent {@code FixedSizeTextChunker}）：
 * <ul>
 *   <li>归一化：修复 URL 内被换行拆开的情况，但不误吞段落换行</li>
 *   <li>英文 '.' 不再无条件作为边界，避免切断 URL 域名</li>
 *   <li>边界回退距离不超过 overlap，防止高度重复的相邻块</li>
 * </ul>
 *
 * <p>适用场景：PDF、TXT 等无明显结构的纯文本文档。
 */
@Component
public class FixedSizeChunker implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedSizeChunker.class);

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.FIXED_SIZE;
    }

    /**
     * 对文本做固定大小分块。
     *
     * @param text        待分块的原始文本
     * @param chunkSize   目标块大小（字符数）
     * @param overlapSize 相邻块重叠字符数
     * @return 有序文本块列表
     */
    @Override
    public List<String> chunk(String text, int chunkSize, int overlapSize) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        // 归一化文本（修复 URL 断行、中文软换行）
        String normalized = normalizeText(text);

        int size = Math.max(1, chunkSize);
        int overlap = Math.max(0, overlapSize);
        // overlap 不能超过 chunkSize - 1，否则会导致死循环
        if (size > 1) {
            overlap = Math.min(overlap, size - 1);
        } else {
            overlap = 0;
        }

        int len = normalized.length();
        List<String> chunks = new ArrayList<>();

        int start = 0;
        int lastEnd = -1;

        while (start < len) {
            int targetEnd = Math.min(start + size, len);
            // 尝试在语义边界处截断
            int end = adjustToBoundary(normalized, start, targetEnd, overlap);

            // 强制推进，避免回退过头导致重复或停滞
            if (end <= start || end <= lastEnd) {
                end = targetEnd;
            }

            String content = normalized.substring(start, end);
            if (StringUtils.hasText(content.strip())) {
                chunks.add(content);
            }

            lastEnd = end;
            if (end >= len) {
                break;
            }

            // 下一块的起始位置保留 overlap 重叠
            int nextStart = Math.max(0, end - overlap);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        log.debug("固定大小分块完成: 原文长度={}, 块数={}", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 调整分块边界，优先在语义边界处截断。
     * 优先级：换行 > 中文句末标点（。！？）> 英文句末标点（后跟空白才算边界，避免切断 URL）
     *
     * @param text      已归一化的文本
     * @param start     当前块起始位置
     * @param targetEnd 原始目标结束位置（不含）
     * @param overlap   最大回退字符数
     * @return 调整后的结束位置（不含）
     */
    private int adjustToBoundary(String text, int start, int targetEnd, int overlap) {
        if (targetEnd <= start) {
            return targetEnd;
        }
        int maxLookback = Math.min(overlap, targetEnd - start);
        if (maxLookback <= 0) {
            return targetEnd;
        }

        // 1) 换行符：最优边界
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) break;
            if (text.charAt(pos) == '\n') return pos + 1;
        }

        // 2) 中文句末标点：。！？
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) break;
            char c = text.charAt(pos);
            if (c == '。' || c == '！' || c == '？') return pos + 1;
        }

        // 3) 英文句末标点：后面必须是空白/换行/字符串结束，且前面不是 URL 字符（避免切断 URL）
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) break;
            char c = text.charAt(pos);
            if (c == '.' || c == '!' || c == '?') {
                // '!' 也是 URL 合法字符，若紧跟在字母/数字后很可能在 URL 内，跳过
                if (c == '!' && pos > 0 && isUrlChar(text.charAt(pos - 1))) continue;
                int next = pos + 1;
                if (next >= text.length()) return next;
                if (Character.isWhitespace(text.charAt(next))) return next;
            }
        }

        return targetEnd;
    }

    /**
     * 归一化输入文本：
     * <ul>
     *   <li>去除 \r</li>
     *   <li>修复 URL 被换行拆开的情况（如 dingtalk.\ncom）</li>
     *   <li>修复中文词中间的软换行（商\n保通 → 商保通）</li>
     *   <li>正常段落换行保留不变</li>
     * </ul>
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String src = text.replace("\r", "");
        StringBuilder out = new StringBuilder(src.length());

        boolean inUrl = false;

        for (int i = 0; i < src.length(); i++) {
            // 检测 URL 起始
            if (!inUrl && looksLikeUrlStart(src, i)) {
                inUrl = true;
            }

            char c = src.charAt(i);

            if (inUrl) {
                if (Character.isWhitespace(c)) {
                    // 收集连续空白，判断是否是 URL 被断行拆开
                    int j = i;
                    boolean sawNewline = false;
                    while (j < src.length() && Character.isWhitespace(src.charAt(j))) {
                        if (src.charAt(j) == '\n') sawNewline = true;
                        j++;
                    }

                    char prev = (i > 0) ? src.charAt(i - 1) : 0;
                    char next = (j < src.length()) ? src.charAt(j) : 0;

                    // 只在"很像 URL 被拆开"的场景合并换行
                    if (sawNewline && next != 0 && shouldJoinBrokenUrl(prev, next, src, j)) {
                        i = j - 1;
                        continue;
                    }

                    // URL 结束：保留原始空白
                    out.append(src, i, j);
                    inUrl = false;
                    i = j - 1;
                    continue;
                }

                out.append(c);

                // URL 以换行/空白结束，非 URL 字符（括号、中文等）不立即退出，
                // 避免 "https://example.com)" 中的 ')' 提前终止状态导致下一行被误合并
                if (isUrlTerminator(c)) {
                    inUrl = false;
                }
                continue;
            }

            // 非 URL 状态：修复中文词中间的软换行
            if (c == '\n') {
                char prev = (i > 0) ? src.charAt(i - 1) : 0;
                char next = (i + 1 < src.length()) ? src.charAt(i + 1) : 0;
                // 前后均为 CJK 字符（非标点）时认为是软换行，直接去掉
                if (isCjkWordChar(prev) && isCjkWordChar(next)) {
                    continue;
                }
                out.append('\n');
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    /**
     * 判断 URL 内遇到换行时，是否应合并（去除换行继续拼接 URL）。
     * 如果下一行像列表项（"2." 等），则绝不合并。
     */
    private boolean shouldJoinBrokenUrl(char prev, char next, String s, int nextIndex) {
        // 列表项开头，不合并
        if (isListItemStart(s, nextIndex)) {
            return false;
        }
        // 典型 URL 断行场景
        if (prev == '.' && Character.isLetter(next)) return true;  // dingtalk.\ncom
        if (prev == '/' || prev == '?' || prev == '&' || prev == '='
                || prev == '#' || prev == '%' || prev == '-' || prev == '_'
                || prev == ':') return true;
        if (next == '/' || next == '?' || next == '&' || next == '=' || next == '#') return true;
        return false;
    }

    /** 判断是否是数字列表项的开头（如 "2." "10)"） */
    private boolean isListItemStart(String s, int i) {
        int p = i;
        // 跳过行首空格/制表符
        while (p < s.length() && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) p++;
        int start = p;
        while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
        if (p == start) return false;
        if (p < s.length() && (s.charAt(p) == '.' || s.charAt(p) == '）' || s.charAt(p) == ')')) {
            return true;
        }
        return false;
    }

    /** 判断字符串从位置 i 开始是否像 URL（http:// 或 https://） */
    private boolean looksLikeUrlStart(String s, int i) {
        return s.startsWith("http://", i) || s.startsWith("https://", i);
    }

    /** 判断字符是否属于 URL 合法字符集 */
    private boolean isUrlChar(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        return c == '-' || c == '.' || c == '_' || c == '~' || c == ':' || c == '/' || c == '?'
                || c == '#' || c == '[' || c == ']' || c == '@' || c == '!' || c == '$'
                || c == '&' || c == '\'' || c == '(' || c == ')' || c == '*' || c == '+'
                || c == ',' || c == ';' || c == '=' || c == '%';
    }

    /**
     * 判断字符是否是 URL 的终止字符。
     * 中文字符、全角标点等明确不属于 URL，视为终止；ASCII 非 URL 字符（如 ')'、'>'）
     * 可能出现在 Markdown 链接语法 "[text](url)" 的括号内，因此不视为终止，
     * 由空白判断逻辑统一处理。
     */
    private boolean isUrlTerminator(char c) {
        // CJK 字符/全角字符明确终止 URL
        if (isCjkOrFullWidth(c)) return true;
        // 中文/全角标点明确终止 URL
        if (isCjkPunctuation(c)) return true;
        return false;
    }

    /**
     * 判断字符是否为 CJK 词语字符（汉字/全角字母/全角数字），不包含标点。
     * 用于识别中文软换行场景。
     */
    private boolean isCjkWordChar(char c) {
        if (c == 0 || Character.isWhitespace(c)) return false;
        if (!isCjkOrFullWidth(c)) return false;
        return !isCjkPunctuation(c);
    }

    private boolean isCjkOrFullWidth(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private boolean isCjkPunctuation(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || c == '。' || c == '，' || c == '、' || c == '；' || c == '：'
                || c == '！' || c == '？' || c == '（' || c == '）'
                || c == '【' || c == '】' || c == '《' || c == '》'
                || c == '\u201C' || c == '\u201D' || c == '\u2018' || c == '\u2019';
    }
}
