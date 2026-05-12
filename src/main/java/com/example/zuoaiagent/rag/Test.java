package com.example.zuoaiagent.rag;



import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;

import java.util.List;

public class Test {

    @Resource
    public DocumentLoader documentLoader;

    @Resource
    public MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    public MyDocumentEnricher myDocumentEnricher;

    @Resource
    public MyDocumentWriter myDocumentWriter;

    @Resource
    public MyVectorStoreWriter myVectorStoreWriter;




    public  void test2(String[] args) {
        List<Document> documents = documentLoader.loadPdfs();
        List<Document> splitCustomized = myTokenTextSplitter.splitCustomized(documents);
        List<Document> documents1 = myDocumentEnricher.enrichDocumentsBySummary(splitCustomized);
        List<Document> documents2 = myDocumentEnricher.enrichDocumentsByKeyword(documents1);
        myDocumentWriter.writeDocuments(documents2);
        myVectorStoreWriter.storeDocuments(documents2);
    }
   /* public static void test(String[] args) {
        // 抽取：从 PDF 文件读取文档
        PDFReader pdfReader = new PagePdfDocumentReader("knowledge_base.pdf");
        List<Document> documents = pdfReader.read();

// 转换：分割文本并添加摘要
        TokenTextSplitter splitter = new TokenTextSplitter(500, 50);
        List<Document> splitDocuments = splitter.apply(documents);

        SummaryMetadataEnricher enricher = new SummaryMetadataEnricher(chatModel,
                List.of(SummaryMetadataEnricher.SummaryType.CURRENT));
        List<Document> enrichedDocuments = enricher.apply(splitDocuments);

// 加载：写入向量数据库
        vectorStore.write(enrichedDocuments);

// 或者使用链式调用
        vectorStore.write(enricher.apply(splitter.apply(pdfReader.read())));

    }*/
}
