package fun.medrec.spring;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.MinerUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.BreakIterator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.lang.Thread.sleep;

@Slf4j
@SpringBootTest
class ApplicationTests {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    @Autowired
    private AgentService agentService;
    @Autowired
    private VectorService vectorService;


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

    @Autowired
    private MinerUtil minerUtil;
    @Autowired
    private TextUtil textUtil;
    private final RestClient restClient = RestClient.create();

    private static final String API_KEY = "sk-1ff40835f4a14e39970c2bbfacde8156";
    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String MODEL = "qwen3.6-plus";
    String local = "D:/Source/windows/Downloads/高血压 - 副本.pdf";



    @Test
    void test01() throws Exception {
        Path path = Paths.get(local);
        byte[] content = Files.readAllBytes(path);

        // 创建Mock文件
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "高血压 - 副本.pdf",
                "application/pdf",
                content
        );

        List<MultipartFile> files = List.of(mockFile);

        String s = minerUtil.uploadAndParse(files);
        while (true) {
            MinerUtil.PollingResult zipUrl = minerUtil.getZipUrl(s);
            sleep(1000);
            if (zipUrl.isSuccess()) {
                List<String> zipUrls = zipUrl.getZipUrls();
                for (String url : zipUrls) {
                    log.info("下载文件:{}", url);
                    List<MinerUtil.ContentItem> contentItems = minerUtil.handleParseResult(url);
                    String outputPath01 = "D:/Source/windows/Desktop/summarized01.json";
                    String outputPath02 = "D:/Source/windows/Desktop/summarized02.json";
                    String outputPath03 = "D:/Source/windows/Desktop/summarized03.json";
                    String outputPath04 = "D:/Source/windows/Desktop/summarized04.json";




                    log.info("开始写入向量库");
                    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    objectMapper.writeValue(new File(outputPath01), contentItems);
                    List<TextSegment> textSegments = textUtil.textSegmentsFromMiner(contentItems, 0);
                    log.info("开始写入向量库");
                    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    objectMapper.writeValue(new File(outputPath02), textSegments);
                    log.info("开始写入向量库");
                    List<TextSegment> textSegments1 = textUtil.strengthenWithSpilt(textSegments);
                    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    objectMapper.writeValue(new File(outputPath03), textSegments1);
                    log.info("开始写入向量库");
                    List<Document> docs = textUtil.toDocsWithSplit(textSegments1);
                    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                    objectMapper.writeValue(new File(outputPath04), docs);
                }
                break;
            }
        }
    }


    @Test
    public void chatWithImage() {
        // 图片路径
        String imagePath = "D:/Source/windows/Downloads/70bbbbe7-f3df-42dc-9b12-f7aa439164d2/images/0deaab291b3b0c512f19c583349414fb1e634ad3dd27b2e04d8b803aea19af7c.jpg";

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(API_KEY)
                .baseUrl(API_URL)
                .build();

        OpenAiChatOptions build = OpenAiChatOptions.builder()
                .model(MODEL)
                .temperature(0.8)
                .maxTokens(10000)
                .build();


        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();

        ChatClient chatClient = ChatClient.builder(model)
                .defaultOptions(build)
                .build();


        log.info("123456");

        try {

            // 使用 Resource 而不是 URL
            org.springframework.core.io.Resource imageResource = new org.springframework.core.io.FileSystemResource(imagePath);

            String out = chatClient.prompt()
                    .user(userSpec -> userSpec
                            .text("请描述这张图片的内容")
                            .media(MimeTypeUtils.IMAGE_JPEG, imageResource)
                    )
                    .call()
                    .content();

            log.info("图片描述: {}", out);

        } catch (Exception e) {
            log.error("Error: ", e);
        }

    }

    @Test
    void testRagSingleQuestion() {
        String complexEnglish = "Dr. Smith, who works at Google Inc., said: \"The project is amazing!\" " +
                "However, Prof. Johnson's research (published in Science, Vol. 123, pp. 45-67) suggests otherwise. " +
                "What's your opinion? Email me at john.doe@example.com for details. " +
                "Mr. Anderson, CEO of Tech Corp., announced: 'We've raised $1.5M!' " +
                "Isn't that great? Yes, it's wonderful!";

        List<String> sentences = splitEnglishSentences(complexEnglish);

        for (int i = 0; i < sentences.size(); i++) {
            System.out.println("句子 " + (i + 1) + ": " + sentences.get(i));
            System.out.println("---");
        }
    }

    public static List<String> splitEnglishSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.CHINESE);
        iterator.setText(text);

        int start = iterator.first();
        int end = iterator.next();

        while (end != BreakIterator.DONE) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            start = end;
            end = iterator.next();
        }

        return sentences;
    }

    @Test
    void testRagAllQuestions() {
        // 1. 初始化 AI Agent（和你代码一致）
        Agent byId = agentService.getById(2);
        AiAgent aiAgent = new AiAgent(byId);
        Vector vector = vectorService.getById(2);
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