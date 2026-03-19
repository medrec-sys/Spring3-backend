package fun.medrec.spring;


import fun.medrec.spring.domain.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@Slf4j
@SpringBootTest
class ApplicationTests {

    @Autowired
    VectorService vectorService;



    @Test
    void test01() {
        PageDTO<Vector> objectPageDTO = new PageDTO<>();
        Vector vector = vectorService.getPage(objectPageDTO).getRows().getFirst();
        MyVectorStore vectorStore = new MyVectorStore(vector);

        String pdfPath = "D:\\Source\\windows\\Downloads\\高血压.pdf";

        TextUtil.TextData textData = TextUtil.readPdf(pdfPath);
        log.info("读取pdf完成");

        textData = vectorStore.mergeSentence(textData);
        log.info("合并句子完成");

        List<Document> documents = TextUtil.TextToDocument(textData);
        vectorStore.addDocuments(documents);
        log.info("向量存储完成");
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("老年高血压").topK(5).build());

        results.forEach(
                document -> System.out.println(document.getText())
        );

        vectorStore.release();

    }

    @Test
    void text02() {

    }
}