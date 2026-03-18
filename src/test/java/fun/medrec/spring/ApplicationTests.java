package fun.medrec.spring;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.VectorService;
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
    EmbeddingModel embeddingModel;

    @Autowired
    VectorService vectorService;

    @Autowired
    VectorStore vectorStore;


    @Test
    void test01() {
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

    @Test
    void text02() {
        PageDTO<Vector> objectPageDTO = new PageDTO<>();
        PageVO<Vector> pages = vectorService.getPage(objectPageDTO);
        log.info("{}", JSON.toJSONString(pages, SerializerFeature.PrettyFormat));
    }
}