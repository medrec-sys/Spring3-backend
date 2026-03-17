package fun.medrec.spring;


import fun.medrec.spring.utils.VectorUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@Slf4j
@SpringBootTest
class ApplicationTests {
    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    VectorStore vectorStore;


    @Test
    void testExtractPdfText() {
        String pdfPath = "D:\\Source\\windows\\Downloads\\高血压.pdf";

        TextUtil.TextData textData = TextUtil.readPdf(pdfPath);
        log.info("读取pdf完成");

        textData = VectorUtil.mergeSentence(textData, embeddingModel);
        log.info("合并句子完成");

        List<Document> documents = TextUtil.TextToDocument(textData);

        VectorUtil.addDocuments(vectorStore, documents);
        log.info("向量存储完成");

        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder().query("ACEI的作用").topK(5).build());

        results.forEach(
                document -> System.out.println(document.getText())
        );

    }
}