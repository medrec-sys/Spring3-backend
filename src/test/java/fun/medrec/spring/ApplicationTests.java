package fun.medrec.spring;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.HttpService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import java.util.Arrays;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@SpringBootTest
class ApplicationTests {
    @Autowired
    private VectorService vectorService;
    @Autowired
    private AgentService agentService;
    @Autowired
    private HttpService httpService;

    String path = "D:/Source/windows/Desktop/hypertension_split.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> testQuestions = Arrays.asList(
            // ====================== 一级：最简单（基础概念）======================
            "我国高血压患病率呈现什么趋势？",
            "高血压的诊断标准是什么？",
            "正常血压的数值范围是多少？",
            "高血压治疗的根本目标是什么？",
            "一般高血压患者的降压目标是多少？",
            "我国人群高血压最重要的两个危险因素是什么？",
            "常用一线降压药分为哪五大类？",
            "每人每天食盐摄入量应低于多少克？",

            // ====================== 二级：中等难度（定义/数值）======================
            "我国高血压的知晓率、治疗率、控制率分别是多少？",
            "家庭血压诊断高血压的标准是多少？",
            "动态血压24小时平均诊断高血压标准是什么？",
            "高血压患者评估的靶器官包括哪些？",
            "降压治疗应在多长时间内逐渐达标？",
            "生活方式干预包括哪几项主要措施？",
            "高血压按血压水平分为几级？",

            // ====================== 三级：较难（细节/指南原文）======================
            "正常高值血压的范围是多少？",
            "高血压危险分层分为哪几个等级？",
            "诊室血压测量前需要安静休息几分钟？",
            "高血压患者每周运动几天？每次多长时间？",
            "哪些人群需要测量站立位血压？",
            "饮酒的男性每日酒精摄入量不应超过多少克？",
            "血压多少时推荐起始联合治疗？",
            "我国高血压人群最主要的并发症是什么？",

            // ====================== 四级：高难度（指南细节）======================
            "18~24岁、25~34岁、35~44岁青年高血压患病率分别是多少？",
            "左心室肥厚常用哪两种检查方法？",
            "中危高血压患者可以先观察多久再决定用药？",
            "低危高血压患者可以观察多久？",
            "高血压合并糖尿病患者的降压目标是多少？",
            "哪些研究证实CCB为基础的方案可降低脑卒中风险？",
            "PATS研究证实哪种药物可降低脑卒中再发风险？",
            "FEVER研究中降压可使脑卒中风险降低多少？",

            // ====================== 五级：最难（综合/推理/全文检索）======================
            "请描述初诊高血压患者从评估到治疗的完整流程。",
            "老年高血压与普通成年人治疗有哪些关键不同？",
            "动态血压和家庭血压各有什么优势？",
            "请总结五大类降压药的适用特点。",
            "高血压的心血管危险因素包括哪些？",
            "哪些情况属于高血压的靶器官损害？",
            "请简述我国高血压流行的两个显著特点。",
            "生活方式干预中减重的合理目标与速度是什么？",

            // ====================== 专项：青年/青少年高血压（你重点关心）======================
            "青年高血压患病率分别是多少？",
            "青年高血压的主要危险因素有哪些？",
            "青少年高血压的治疗原则是什么？",
            "青少年高血压的药物治疗原则是什么？",
            "青少年高血压的生活方式干预包括哪些？",
            "青年高血压是否有专门推荐的药物？",
            "青少年高血压的诊断标准是什么？",
            "青少年高血压的降压目标是什么？"
    );

    @Test
    void test01() throws Exception {
        List<TextSegment> textSegments = objectMapper.readValue(
                new File(path),
                new TypeReference<>() {
                }
        );


        textSegments = TextUtil.summarizer(textSegments, 2);
        // 保存到JSON文件
        String outputPath01 = "D:/Source/windows/Desktop/summarized.json";

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(new File(outputPath01), textSegments);
    }

    @Test
    void test02() throws Exception {
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);
        String outputPath0 = "D:/Source/windows/Desktop/summarized.json";

        List<TextSegment> textSegments = objectMapper.readValue(
                new File(outputPath0),
                new TypeReference<>() {
                }
        );
        for (int i = 1; i < 2; i++) {
            List<Document> documents = TextUtil.TextToDocuments(textSegments, i);
            myVectorStore.addDocuments(documents);
        }

    }

    @Test
    void test03() {
        Vector byId = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(byId);


        SearchRequest request = SearchRequest.builder()
                .query("初诊高血压患者的管理见表14。表14初诊高血压患者的管理初诊随访判断是否有靶器官损害血压及有关的症状和体征判断是否有继发性高血压的可能治疗的副作用对高血压患者进行心血管综合危险度评估,确定是否要干预其他心血管危险因素影响生活方式改变和药物治疗依从性的障碍给予生活方式指导和药物治疗制定下一次随访日期建议家庭血压监测登记并加入高血压管理\"")
                .topK(50)
                .similarityThreshold(0.9)
                .build();


        List<Document> documents = myVectorStore.similaritySearch(request);

        for (Document document : documents) {
            log.info("doc{}\n\n\n\n", JSON.toJSONString( document, SerializerFeature.PrettyFormat));
        }




    }

    @Test
    void test04() {
        Agent byId = agentService.getById(1);
        AiAgent aiAgent = new AiAgent(byId);
        Vector vector = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(vector);
        aiAgent.addVectorStore(myVectorStore);
        String result = aiAgent.chat("青少年治疗高血压的药物以及使用方法和要点")
                .collectList()
                .map(list -> String.join("", list))
                .block();  // 阻塞等待
        System.out.println(result);
    }

    @Test
    void test05() {
        String path = "D:/Source/windows/Downloads/高血压.pdf";

        try (java.io.FileInputStream fis = new java.io.FileInputStream(path)) {
            MultipartFile multipartFile = new MockMultipartFile(
                    "file",                    // 表单字段名
                    "高血压.pdf",               // 原始文件名
                    "application/pdf",         // Content-Type
                    fis                        // 输入流
            );

            FileData fileData = new FileData(multipartFile);
            Result<List<TextSegment>> result = httpService.fileToMd(fileData);
            log.info("{}", result);
        } catch (IOException e) {
            log.error("读取文件失败: {}", path, e);
        }
    }

    @Test
    void testRagAllQuestions() {
        // 1. 初始化 AI Agent（和你代码一致）
        Agent byId = agentService.getById(1);
        AiAgent aiAgent = new AiAgent(byId);
        Vector vector = vectorService.getById(1);
        MyVectorStore myVectorStore = new MyVectorStore(vector);
        aiAgent.addVectorStore(myVectorStore);

        // 2. 生成文件名（带时间戳，避免覆盖）
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filePath = "RAG_高血压测试问答_" + time + ".txt";

        // 3. 批量提问 + 写入文件
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            writer.println("========== 高血压 RAG 检索测试问答 ==========\n");

            for (int i = 0; i < testQuestions.size(); i++) {
                String question = testQuestions.get(i);
                System.out.println("正在测试第 " + (i + 1) + " 题：" + question);

                // 4. 调用 RAG 对话（和你代码逻辑一致）
                String answer = Flux.from(aiAgent.chat(question))
                        .collectList()
                        .map(list -> String.join("", list))
                        .block();

                // 5. 写入文件：题号 + 问题 + 答案
                writer.println("【第 " + (i + 1) + " 题】");
                writer.println("问题：" + question);
                writer.println("回答：" + answer);
                writer.println("--------------------------------------------------\n");

                // 控制台也输出
                System.out.println("回答：" + answer);
                System.out.println("--------------------------------------------------");
            }

            System.out.println("\n✅ 所有测试完成！问答已写入文件：" + filePath);

        } catch (IOException e) {
            System.err.println("❌ 文件写入失败：" + e.getMessage());
        }
    }
}