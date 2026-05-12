package com.example.zuoaiagent.knowledge.chunk.strategy;

import com.example.zuoaiagent.knowledge.chunk.ChunkingMode;
import com.example.zuoaiagent.knowledge.chunk.ChunkingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构感知分块器（Markdown 友好版）
 *
 * <p>不改写文本，仅在"块"边界切分，块类型包括：
 * <ul>
 *   <li>Heading（# ~ ######）：标题行，独立块</li>
 *   <li>CodeFence（```...```）：代码围栏，整体作为一个块</li>
 *   <li>Atomic（单行图片 ![]() / 链接 []()）：原子行，独立块</li>
 *   <li>Para：其他内容，按空行切段后按 min/target/max 预算合并</li>
 * </ul>
 *
 * <p>通过 {@code chunkSize} 参数传入 target，{@code overlapSize} 传入 overlap；
 * min = chunkSize / 2，max = chunkSize * 4 / 3（粗略估算，保持与接口兼容）。
 *
 * <p>参照 ragent {@code StructureAwareTextChunker}。适用于 Markdown 格式文档。
 */
@Component
public class StructureAwareChunker implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(StructureAwareChunker.class);

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.*$");
    private static final Pattern CODE_FENCE = Pattern.compile("^```.*$");
    private static final Pattern ATOMIC_IMAGE = Pattern.compile("^!\\[[^]]*]\\([^)]+\\)(?:\\s*\"[^\"]*\")?\\s*$");
    private static final Pattern ATOMIC_LINK = Pattern.compile("^\\[[^]]+]\\([^)]+\\)\\s*$");

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.STRUCTURE_AWARE;
    }

    /**
     * 按 Markdown 结构感知分块。
     *
     * @param text        待分块的 Markdown 文本
     * @param chunkSize   目标块大小（字符数，对应 target）
     * @param overlapSize 重叠字符数（追加到下一块开头）
     * @return 有序文本块列表
     */
    @Override
    public List<String> chunk(String text, int chunkSize, int overlapSize) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        // 统一行尾：Windows \r\n → \n，老 Mac \r → \n
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        int target = Math.max(1, chunkSize);
        int max = target * 4 / 3;   // max 约为 target 的 1.33 倍
        int min = target / 2;        // min 约为 target 的 0.5 倍
        int overlap = Math.max(0, overlapSize);

        // 1) 将文本扫描成块列表（记录原文 start/end 下标）
        List<Block> blocks = segmentToBlocks(text);

        if (blocks.isEmpty()) {
            // 极端兜底：整体作为一个块
            return List.of(text);
        }

        // 2) 按 min/target/max 预算打包成 chunk（只在块边界切分）
        List<int[]> ranges = packBlocksToChunks(blocks, min, target, max);

        // 3) 物化为字符串，必要时追加 overlap
        List<String> result = materialize(text, ranges, overlap);

        log.debug("结构感知分块完成: 原文长度={}, 块数={}", text.length(), result.size());
        return result;
    }

    // -------------------- 块模型 --------------------

    /** 块类型枚举 */
    enum BlockKind {HEADING, CODE, ATOMIC, PARA}

    /**
     * 表示原文中一个结构块的 [start, end) 范围。
     */
    private static class Block {
        final BlockKind kind;
        final int start;   // 在原文中的起始（含）
        final int end;     // 在原文中的结束（不含）

        Block(BlockKind kind, int start, int end) {
            this.kind = kind;
            this.start = start;
            this.end = end;
        }

        BlockKind getKind() {
            return kind;
        }

        int getStart() {
            return start;
        }

        int getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return "Block{kind=" + kind + ", start=" + start + ", end=" + end + '}';
        }
    }

    // -------------------- 1) 扫描成块 --------------------

    /** 将文本线性扫描，识别各类结构块。 */
    private List<Block> segmentToBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        int n = text.length();
        int pos = 0;

        boolean inFence = false;
        int fenceStart = -1;
        boolean inPara = false;
        int paraStart = -1;

        while (pos < n) {
            int lineEnd = indexOfNl(text, pos);
            // lineEndNl：包含换行符的结束位置
            int lineEndNl = (lineEnd < n && text.charAt(lineEnd) == '\n') ? lineEnd + 1 : lineEnd;
            String line = text.substring(pos, lineEnd);
            String trimmed = trimRight(line);

            // ---- 代码围栏处理 ----
            if (!inFence && CODE_FENCE.matcher(trimmed).matches()) {
                if (inPara) {
                    blocks.add(new Block(BlockKind.PARA, paraStart, pos));
                    inPara = false;
                }
                inFence = true;
                fenceStart = pos;
                pos = lineEndNl;
                continue;
            }

            if (inFence) {
                if (CODE_FENCE.matcher(trimmed).matches()) {
                    // 结束围栏（含结束行）
                    blocks.add(new Block(BlockKind.CODE, fenceStart, lineEndNl));
                    inFence = false;
                }
                pos = lineEndNl;
                continue;
            }

            // ---- 空行：段落边界 ----
            if (trimmed.isEmpty()) {
                if (inPara) {
                    blocks.add(new Block(BlockKind.PARA, paraStart, pos));
                    inPara = false;
                }
                pos = lineEndNl;
                continue;
            }

            // ---- 标题/原子行：独立块 ----
            if (HEADING.matcher(trimmed).matches()) {
                if (inPara) {
                    blocks.add(new Block(BlockKind.PARA, paraStart, pos));
                    inPara = false;
                }
                blocks.add(new Block(BlockKind.HEADING, pos, lineEndNl));
                pos = lineEndNl;
                continue;
            }
            if (ATOMIC_IMAGE.matcher(trimmed).matches() || ATOMIC_LINK.matcher(trimmed).matches()) {
                if (inPara) {
                    blocks.add(new Block(BlockKind.PARA, paraStart, pos));
                    inPara = false;
                }
                blocks.add(new Block(BlockKind.ATOMIC, pos, lineEndNl));
                pos = lineEndNl;
                continue;
            }

            // ---- 普通行：并入段落 ----
            if (!inPara) {
                inPara = true;
                paraStart = pos;
            }
            pos = lineEndNl;
        }

        // 收尾：未闭合的围栏或段落
        if (inFence) {
            blocks.add(new Block(BlockKind.CODE, fenceStart, n));
        } else if (inPara) {
            blocks.add(new Block(BlockKind.PARA, paraStart, n));
        }

        return coalesceGaps(blocks, text);
    }

    /**
     * 将相邻块之间的空白间隙并入前一个块，避免产生零散的空白块。
     */
    private List<Block> coalesceGaps(List<Block> blocks, String text) {
        if (blocks.isEmpty()) return blocks;
        List<Block> out = new ArrayList<>();
        Block prev = blocks.get(0);
        for (int i = 1; i < blocks.size(); i++) {
            Block cur = blocks.get(i);
            if (isAllBlank(text, prev.end, cur.start)) {
                // 将中间空白并入 prev
                prev = new Block(prev.kind, prev.start, cur.start);
            }
            out.add(prev);
            prev = cur;
        }
        out.add(prev);
        return out;
    }

    // -------------------- 2) 打包成 chunk --------------------

    /** 按 min/target/max 预算将块列表打包为 [start, end) 范围列表。 */
    private List<int[]> packBlocksToChunks(List<Block> blocks, int min, int target, int max) {
        List<int[]> ranges = new ArrayList<>();
        int i = 0;
        while (i < blocks.size()) {
            int chunkStart = blocks.get(i).start;
            int chunkEnd = blocks.get(i).end;
            int size = chunkEnd - chunkStart;
            int j = i + 1;

            while (j < blocks.size()) {
                Block b = blocks.get(j);
                int afterAdd = b.end - chunkStart;
                if (afterAdd <= max) {
                    // 还能继续合并
                    chunkEnd = b.end;
                    size = afterAdd;
                    j++;
                } else {
                    // 超过 max；若当前 size 仍然太小，忍一次超限
                    if (size < min) {
                        chunkEnd = b.end;
                        j++;
                    }
                    break;
                }
            }
            ranges.add(new int[]{chunkStart, chunkEnd});
            i = j;
        }

        // 最后一个 chunk 过小时尝试与倒数第二个合并
        if (ranges.size() >= 2) {
            int[] last = ranges.get(ranges.size() - 1);
            if (last[1] - last[0] < Math.min(min, target / 2)) {
                int[] prev = ranges.get(ranges.size() - 2);
                if (last[1] - prev[0] <= max * 2) {
                    prev[1] = last[1];
                    ranges.remove(ranges.size() - 1);
                }
            }
        }
        return ranges;
    }

    // -------------------- 3) 物化为字符串 --------------------

    /** 将 [start, end) 范围列表物化为字符串，必要时在块开头追加上一块的尾部（overlap）。 */
    private List<String> materialize(String text, List<int[]> ranges, int overlap) {
        if (ranges.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        String prevTail = null;

        for (int[] range : ranges) {
            String body = text.substring(range[0], range[1]);
            // 追加上一块的尾部重叠内容
            if (overlap > 0 && prevTail != null && !prevTail.isEmpty()) {
                body = prevTail + body;
            }
            if (StringUtils.hasText(body.strip())) {
                out.add(body);
            }
            // 记录本块尾部字符，供下一块使用
            if (overlap > 0) {
                String raw = text.substring(range[0], range[1]);
                int len = raw.length();
                prevTail = len <= overlap ? raw : raw.substring(len - overlap);
            }
        }
        return out;
    }

    // -------------------- 工具方法 --------------------

    /** 返回从 {@code from} 开始第一个 '\n' 的位置，若不存在返回字符串长度。 */
    private int indexOfNl(String s, int from) {
        int p = s.indexOf('\n', from);
        return p < 0 ? s.length() : p;
    }

    /** 保留左侧空白，去掉右侧空白（不含换行）。 */
    private String trimRight(String s) {
        int r = s.length();
        while (r > 0 && Character.isWhitespace(s.charAt(r - 1))
                && s.charAt(r - 1) != '\n' && s.charAt(r - 1) != '\r') {
            r--;
        }
        return s.substring(0, r);
    }

    /** 判断 [from, to) 范围内是否全为空白字符。 */
    private boolean isAllBlank(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') return false;
        }
        return true;
    }
}
