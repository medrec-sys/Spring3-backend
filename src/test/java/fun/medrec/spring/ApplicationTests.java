package fun.medrec.spring;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

@Slf4j
@SpringBootTest
class ApplicationTests {
    @Autowired
    private VectorService vectorService;
    @Autowired
    private AgentService agentService;

    String path = "D:/Source/windows/Desktop/hypertension_split.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test01() throws Exception {
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);

        List<TextSegment> textSegments = objectMapper.readValue(
                new File(path),
                new TypeReference<>() {
                }
        );


        textSegments = TextUtil.summarizer(textSegments, 1);
        // 保存到JSON文件
        String outputPath01 = "D:/Source/windows/Desktop/summarized.json";

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(new File(outputPath01), textSegments);


        TextSegment textSegment = myVectorStore.buildTree(textSegments);
        // 保存到JSON文件
        String outputPath02 = "D:/Source/windows/Desktop/merge.json";

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(new File(outputPath02), textSegment);
    }

    @Test
    void test02() throws Exception {
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);
        String outputPath0 = "D:/Source/windows/Desktop/merge.json";

        TextSegment textSegment = objectMapper.readValue(
                new File(outputPath0),
                new TypeReference<>() {
                }
        );
        List<Document> documents = TextUtil.TextToDocuments(textSegment, 1);
        myVectorStore.addDocuments(documents);
    }

    @Test
    void test03() throws Exception {
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);

        SearchRequest request = SearchRequest.builder()
                .query("胄少年高血压的治疗方法")
                .topK(15)                       // Return top 5 results
                .similarityThreshold(0.9)      // Only return results with similarity score >= 0.7
                .build();

        List<Document> documents = myVectorStore.similaritySearch(request);
        for (Document document : documents) {
            log.info("2{}", document.getText());
        }
        log.info("111{}", documents.size());
    }

    @Test
    void test04() throws Exception {
        Agent byId = agentService.getById(1);
        AiAgent aiAgent = new AiAgent(byId);
        Vector vector = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(vector);
        aiAgent.addVectorStore(myVectorStore);
        String result = aiAgent.chat("青少年高血压如何治疗")
                .collectList()
                .map(list -> String.join("", list))
                .block();  // 阻塞等待
        System.out.println(result);
    }
}