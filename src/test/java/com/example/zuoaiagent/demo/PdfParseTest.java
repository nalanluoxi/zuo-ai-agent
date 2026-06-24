package com.example.zuoaiagent.demo;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 独立测试 PDF 文字提取逻辑（不启动 Spring 容器）。
 *
 * <p>复刻 {@code DocumentIngestionService#parseToText} + {@code ocrPdf} 完整链路：
 * 先尝试文字图层提取，为空则自动走 Tesseract OCR 兜底，输出前两页内容。
 */
public class PdfParseTest {

    static {
        // macOS Homebrew 安装的 tesseract 原生库路径
        System.setProperty("jna.library.path", "/opt/homebrew/lib");
    }

    private static final String PDF_PATH =
            "/Users/nalan/IdeaProjects/ai/zuo-ai-agent/src/main/resources/docs/pdf2/" +
            "投资估价 (阿斯沃斯·达摩达兰) (z-library.sk, 1lib.sk, z-lib.sk) (1)(1).pdf";

    private static final String TESS_DATA_PATH = "/opt/homebrew/share/tessdata";

    @Test
    void testParsePdfFirst2Pages() throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(PDF_PATH));
        String docName = Paths.get(PDF_PATH).getFileName().toString();

        System.out.println("文件大小: " + fileBytes.length + " bytes");
        System.out.println("文件名: " + docName);
        System.out.println("=".repeat(60));

        // 1. 先尝试文字图层提取（与 DocumentIngestionService#parseToText 相同）
        Resource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return docName;
            }
        };

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(1)
                .build();

        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
        List<Document> pages = reader.get();

        System.out.println("PDF 文字图层页数: " + pages.size());
        System.out.println("=".repeat(60));

        boolean hasTextLayer = !pages.isEmpty() &&
                pages.stream().anyMatch(p -> !p.getFormattedContent().isBlank());

        if (hasTextLayer) {
            // 有文字图层，直接输出前两页
            int limit = Math.min(2, pages.size());
            for (int i = 0; i < limit; i++) {
                String text = pages.get(i).getFormattedContent();
                System.out.printf("--- 第 %d 页（文字图层，字符数: %d）---%n", i + 1, text.length());
                System.out.println(text);
                System.out.println();
            }
        } else {
            // 2. 文字图层为空 → OCR 兜底（与 DocumentIngestionService#ocrPdf 相同）
            System.out.println("文字图层为空，启用 Tesseract OCR 识别前两页...");
            System.out.println("=".repeat(60));

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(TESS_DATA_PATH);
            tesseract.setLanguage("chi_sim+eng");

            try (PDDocument pdDocument = Loader.loadPDF(fileBytes)) {
                PDFRenderer renderer = new PDFRenderer(pdDocument);
                int totalPages = pdDocument.getNumberOfPages();
                System.out.println("PDF 总页数: " + totalPages);
                System.out.println("=".repeat(60));

                //int limit = Math.min(2, totalPages);
                for (int i = 10; i < 50; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 300);
                    try {
                        String pageText = tesseract.doOCR(image);
                        System.out.printf("--- 第 %d 页（OCR，字符数: %d）---%n", i + 1, pageText.length());
                        System.out.println(pageText);
                        System.out.println();
                    } catch (TesseractException e) {
                        System.out.printf("--- 第 %d 页 OCR 失败: %s ---%n", i + 1, e.getMessage());
                    }
                }
            }
        }
    }
}
