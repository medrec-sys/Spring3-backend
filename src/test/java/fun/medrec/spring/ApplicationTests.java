package fun.medrec.spring;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@SpringBootTest
class ApplicationTests {
    @Autowired
    private VectorService vectorService;

    String path = "D:/Source/windows/Desktop/merge.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test01() throws Exception {
//        List<TextSegment> textSegments = objectMapper.readValue(
//                new File(path),
//                new TypeReference<>() {
//                }
//        );
//
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);

        SearchRequest request = SearchRequest.builder()
                .query("青少年高血压如何治疗")
                .topK(5)                       // Return top 5 results
                .similarityThreshold(0.5)      // Only return results with similarity score >= 0.7
                .build();

        List<Document> documents = myVectorStore.similaritySearch(request);
        for (Document document : documents) {
            log.info("{}", JSON.toJSONString( document, SerializerFeature.PrettyFormat));
            log.info("{}", document.getText());
            log.info("{}", document.getMetadata());


        }
//
//        textSegments = TextUtil.summarizer(textSegments, 1);
//        // 保存到JSON文件
//        String outputPath01 = "D:/Source/windows/Desktop/summarized.json";
//
//        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
//        objectMapper.writeValue(new File(outputPath01), textSegments);
//
//
//
//        TextSegment textSegment = myVectorStore.buildTree(textSegments);
//        // 保存到JSON文件
//        String outputPath02 = "D:/Source/windows/Desktop/merge.json";
//
//        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
//        objectMapper.writeValue(new File(outputPath02), textSegment);

//        TextSegment textSegment  = objectMapper.readValue(
//                new File(path),
//                new TypeReference<>() {
//                }
//        );
//        List<Document> documents = TextUtil.TextToDocuments(textSegment, 1);
//        // 保存到JSON文件
//        String outputPath03 = "D:/Source/windows/Desktop/doc.json";
//        myVectorStore.addDocuments(documents);
//
//        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
//        objectMapper.writeValue(new File(outputPath03), documents);

    }
}