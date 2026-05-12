package com.example.zuoaiagent.knowledge.ingestion;

import com.example.zuoaiagent.knowledge.chunk.ChunkingStrategy;
import com.example.zuoaiagent.knowledge.chunk.strategy.FixedSizeChunker;
import com.example.zuoaiagent.knowledge.chunk.strategy.StructureAwareChunker;
import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.example.zuoaiagent.knowledge.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档入库服务（核心 ETL 流水线）
 *
 * <p>完整链路：
 * <pre>
 *   读取文件字节（DB）
 *     → DocumentParser 解析为文本
 *     → ChunkingStrategy 文本分块
 *     → VectorStore.add()（Spring AI 内部完成 Embedding + 写入 PgVector）
 *     → 更新文档状态（success / failed）
 * </pre>
 *
 * <p>通过 {@link Async} 注解在独立 {@code ingestionExecutor} 线程池中异步执行，
 * 避免阻塞文件上传接口。向量化调用链路：
 * {@code VectorStore.add()} → {@code RoutingEmbeddingService}（含三态熔断）→ Ollama bge-m3。
 *
 * <p>参照 ragent {@code ingestion} 包中 {@code IndexerNode} 与 {@code ChunkerNode} 的整体设计，
 * 但简化为单一服务类，不依赖 RocketMQ 事务消息。
 */
@Service
public class DocumentIngestionService {


    static {
        // 只有 Mac 且是 M 系列（通常路径在 /opt/homebrew）时才设置
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            System.setProperty("jna.library.path", "/opt/homebrew/lib");
        }
    }


    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    /** 固定大小分块策略（PDF、TXT 等纯文本） */
    private final FixedSizeChunker fixedSizeChunker;

    /** 结构感知分块策略（Markdown 文档） */
    private final StructureAwareChunker structureAwareChunker;

    /**
     * Spring AI PgVector 向量存储。
     * {@link VectorStore#add(List)} 内部会调用已配置的 EmbeddingModel 完成向量化后写入。
     */
    private final VectorStore vectorStore;

    /** 文档元数据 Mapper */
    private final KnowledgeDocumentMapper documentMapper;

    /** 用于读取 t_knowledge_document_file 表中的文件字节 */
    private final JdbcTemplate jdbcTemplate;

    public DocumentIngestionService(FixedSizeChunker fixedSizeChunker,
                                    StructureAwareChunker structureAwareChunker,
                                    VectorStore vectorStore,
                                    KnowledgeDocumentMapper documentMapper,
                                    JdbcTemplate jdbcTemplate) {
        this.fixedSizeChunker = fixedSizeChunker;
        this.structureAwareChunker = structureAwareChunker;
        this.vectorStore = vectorStore;
        this.documentMapper = documentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tesseract tessdata 数据目录，macOS brew 安装路径：/usr/local/share/tessdata */
    @Value("${ocr.tesseract.data-path:/usr/local/share/tessdata}")
    private String tessDataPath;

    /** 默认分块大小（字符数）。Markdown 时对应 target 参数。 */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /** 默认重叠大小（字符数） */
    private static final int DEFAULT_OVERLAP_SIZE = 128;

    /**
     * 异步触发文档入库流水线。
     *
     * <p>整个方法在 {@code ingestionExecutor} 线程池中执行（{@link Async} 注解），
     * 上传接口无需等待即可返回响应。
     *
     * @param docId 已插入 t_knowledge_document 表的文档 ID
     */
    @Async("ingestionExecutor")
    public void ingest(Long docId) {
        log.info("[入库] 开始处理文档 docId={}", docId);

        // 1. 读取文档元数据
        KnowledgeDocumentDO doc = documentMapper.selectById(docId);
        if (doc == null) {
            log.error("[入库] 文档 {} 不存在，跳过", docId);
            return;
        }

        try {
            // 2. 从数据库读取原始文件字节
            byte[] fileBytes = fetchFileBytes(doc.getFileUrl());
            if (fileBytes == null || fileBytes.length == 0) {
                throw new IllegalStateException("文件内容为空: storageKey=" + doc.getFileUrl());
            }
            log.debug("[入库] docId={} 文件加载完成，大小={} bytes", docId, fileBytes.length);

            // 3. 解析文件为纯文本
            String text = parseToText(fileBytes, doc.getFileType(), doc.getDocName());
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("文档解析结果为空: docId=" + docId);
            }
            log.debug("[入库] docId={} 文本解析完成，长度={} chars", docId, text.length());

            // 4. 选择分块策略：Markdown 使用结构感知，其他使用固定大小
            ChunkingStrategy strategy = selectStrategy(doc.getFileType());
            List<String> chunks = strategy.chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
            log.info("[入库] docId={} 分块完成，共 {} 块，策略={}", docId, chunks.size(), strategy.getType());

            // 5. 逐块构造 Spring AI Document，批量写入 PgVector
            //    vectorStore.add() 内部调用已配置的 EmbeddingModel 完成向量化
            int successCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                try {
                    // 附加文档元数据，便于后续检索时按知识库 / 文档 ID 过滤
                    Map<String, Object> metadata = buildMetadata(doc, i, chunks.size());
                    Document springDoc = new Document(chunk, metadata);
                    vectorStore.add(List.of(springDoc));
                    successCount++;

                } catch (Exception e) {
                    // 单块失败不终止整体入库，记录警告继续处理
                    log.warn("[入库] docId={} 第 {}/{} 块写入失败，跳过。原因: {}",
                            docId, i + 1, chunks.size(), e.getMessage());
                }
            }
            log.info("[入库] docId={} 向量写入完成，成功={}/{}", docId, successCount, chunks.size());

            // 6. 更新文档状态为 success
            updateStatus(docId, "success");

        } catch (Exception e) {
            log.error("[入库] docId={} 入库失败", docId, e);
            // 6. 入库异常时更新文档状态为 failed
            updateStatus(docId, "failed");
        }
    }

    // -------------------- 私有方法 --------------------

    /**
     * 从 t_knowledge_document_file 表读取文件字节。
     *
     * @param storageKey 文件存储键（上传时生成的 UUID_filename）
     * @return 文件字节数组，若不存在返回 null
     */
    private byte[] fetchFileBytes(String storageKey) {
        return jdbcTemplate.queryForObject(
                "SELECT content FROM t_knowledge_document_file WHERE storage_key = ?",
                byte[].class,
                storageKey
        );
    }

    /**
     * 根据文件类型将字节数据解析为纯文本字符串。
     *
     * <ul>
     *   <li>pdf：使用 Spring AI {@link PagePdfDocumentReader}，按页提取文本</li>
     *   <li>md / markdown：使用 Spring AI {@link MarkdownDocumentReader}</li>
     *   <li>txt 及其他：直接按 UTF-8 解码</li>
     * </ul>
     *
     * @param fileBytes 文件字节
     * @param fileType  文件扩展名（小写）
     * @param docName   文档原始文件名（用于 Reader 识别格式）
     * @return 解析后的纯文本
     */
    private String parseToText(byte[] fileBytes, String fileType, String docName) {
        // ByteArrayResource 包装字节数组，让 Spring AI Reader 可以当作 Resource 读取
        Resource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return docName;
            }
        };

        if ("pdf".equalsIgnoreCase(fileType)) {
            // 先尝试文字图层提取
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
            String text = reader.get().stream()
                    .map(Document::getFormattedContent)
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();

            if (!text.isBlank()) {
                return text;
            }

            // 文字图层为空 → 图片型 PDF，使用 OCR 兜底
            log.info("[入库] PDF 文字图层为空，尝试 OCR 识别: docName={}", docName);
            return ocrPdf(fileBytes);
        }

        if ("md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType)) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(true)
                    .withIncludeBlockquote(true)
                    .withAdditionalMetadata("filename", docName)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            return reader.get().stream()
                    .map(Document::getFormattedContent)
                    .reduce("", (a, b) -> a + "\n" + b);
        }

        // txt 或其他纯文本格式：直接 UTF-8 解码
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    /**
     * 根据文件类型选择分块策略。
     * Markdown 文档使用结构感知分块，其他文档类型使用固定大小分块。
     *
     * @param fileType 文件扩展名（小写）
     * @return 对应的 {@link ChunkingStrategy} 实现
     */
    private ChunkingStrategy selectStrategy(String fileType) {
        if ("md".equalsIgnoreCase(fileType) || "markdown".equalsIgnoreCase(fileType)) {
            return structureAwareChunker;
        }
        return fixedSizeChunker;
    }

    /**
     * 构造写入向量库的 Document 元数据，便于后续按知识库 / 文档筛选。
     *
     * @param doc         文档实体
     * @param chunkIndex  当前块序号（0-based）
     * @param totalChunks 总块数
     * @return 元数据 Map
     */
    private Map<String, Object> buildMetadata(KnowledgeDocumentDO doc, int chunkIndex, int totalChunks) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("doc_id", String.valueOf(doc.getId()));
        meta.put("kb_id", String.valueOf(doc.getKbId()));
        meta.put("doc_name", doc.getDocName());
        meta.put("file_type", doc.getFileType());
        meta.put("chunk_index", chunkIndex);
        meta.put("total_chunks", totalChunks);
        return meta;
    }

    /**
     * 更新文档入库状态。
     *
     * @param docId  文档 ID
     * @param status 目标状态：{@code "success"} 或 {@code "failed"}
     */
    private void updateStatus(Long docId, String status) {
        KnowledgeDocumentDO update = new KnowledgeDocumentDO();
        update.setId(docId);
        update.setStatus(status);
        update.setUpdatedBy("system");
        documentMapper.updateById(update);
        log.info("[入库] docId={} 状态更新为 {}", docId, status);
    }

    /**
     * 对图片型 PDF 逐页渲染为图片后用 Tesseract OCR 识别文字。
     *
     * <p>前提：系统已安装 Tesseract（macOS: brew install tesseract tesseract-lang），
     * tessdata 路径通过 {@code tesseract.data.path} 属性配置，默认 /usr/local/share/tessdata。
     */
    private String ocrPdf(byte[] fileBytes) {
        // 防御：tessdata 目录不存在时直接返回空串，避免 native 层 SIGSEGV 崩溃 JVM
        java.io.File tessDir = new java.io.File(tessDataPath);
        if (!tessDir.exists() || !tessDir.isDirectory()) {
            log.error("[OCR] tessdata 目录不存在: {}，跳过 OCR。请确认 ocr.tesseract.data-path 配置正确", tessDataPath);
            return "";
        }

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        // 中英文混合识别；纯英文可改为 "eng"
        tesseract.setLanguage("chi_sim+eng");

        StringBuilder sb = new StringBuilder();
        try (PDDocument pdDocument = Loader.loadPDF(fileBytes)) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            int pageCount = pdDocument.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                // 300 DPI 渲染，OCR 精度较高
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                try {
                    String pageText = tesseract.doOCR(image);
                    sb.append(pageText).append("\n");
                    log.debug("[OCR] 第 {}/{} 页识别完成，文字长度={}", i + 1, pageCount, pageText.length());
                } catch (TesseractException e) {
                    log.warn("[OCR] 第 {}/{} 页识别失败，跳过。原因: {}", i + 1, pageCount, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[OCR] PDF 渲染失败", e);
        }
        return sb.toString();
    }
}
