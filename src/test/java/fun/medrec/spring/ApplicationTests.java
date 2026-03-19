package fun.medrec.spring;


import fun.medrec.spring.domain.Ai.AiAgent;
import fun.medrec.spring.domain.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;


@Slf4j
@SpringBootTest
class ApplicationTests {

    @Autowired
    VectorService vectorService;

    @Autowired
    AgentService agentService;

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



    }

    @Test
    void text02() {
        PageDTO<Agent> agentPageDTO = new PageDTO<>();
        Agent agent = agentService.getPage(agentPageDTO).getRows().getFirst();

        PageDTO<Vector> vectorPageDTO = new PageDTO<>();
        Vector vector = vectorService.getPage(vectorPageDTO).getRows().getFirst();
        MyVectorStore vectorStore = new MyVectorStore(vector);

        AiAgent aiAgent = new AiAgent(agent);
        aiAgent.addVectorStore(vectorStore);
        log.info("开始对话");

        Flux<String> talk = aiAgent.chat("如何治理老年高血压");
        talk.toIterable().forEach(System.out::print);    }
}