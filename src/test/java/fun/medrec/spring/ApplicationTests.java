package fun.medrec.spring;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyQueryExpander;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.MinerUtil;
import fun.medrec.spring.utils.ModelUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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


    // RAG 测试题库（计算机操作系统）5大类，每类10题
    private final List<String> testQuestions = Arrays.asList(
            // ==================== 一、概念定义题（1-10）====================
            "1. 请给出操作系统的完整中文定义",
            "2. 定义进程（Process）",
            "3. 请给出线程（Thread）的中文定义",
//            "4. 定义临界资源（Critical Resource）",
//            "5. 请给出死锁（Deadlock）的完整中文定义",
//            "6. 定义虚拟存储器（Virtual Memory）",
//            "7. 请给出系统调用（System Call）的中文定义",
//            "8. 定义文件控制块（FCB）",
//            "9. 请给出SPOOLing技术的完整中文定义",
//            "10. 定义抖动（Thrashing）",

            // ==================== 二、简答题（11-20）====================
            "11. 操作系统具有哪四大基本特征？请简要说明",
            "12. 进程有哪三种基本状态？它们之间如何转换？",
            "13. 产生死锁的四个必要条件是什么？",
//            "14. 页面置换算法有哪些？请至少列出5种",
//            "15. 虚拟存储器具有哪三个重要特征？",
//            "16. 磁盘调度算法有哪些？请简要说明",
//            "17. 进程通信有哪几种主要方式？",
//            "18. 段页式存储管理的基本原理是什么？",
//            "19. 什么是管程（Monitor）？它由哪几部分组成？",
//            "20. I/O控制方式有哪几种？请按发展顺序列出",

            // ==================== 三、计算/应用题（21-30）====================
            "21. 某系统有3个进程P1、P2、P3，所需资源总数12台，P1最大需求10台已分配5台，P2最大需求4台已分配2台，P3最大需求9台已分配2台，可用资源3台，判断系统是否处于安全状态？若是，请给出一个安全序列。",
            "22. 假设访问内存时间为100ns，快表访问时间为20ns，命中率为90%，计算引入快表后的有效访问时间。",
            "23. 某请求分页系统，页面大小为1KB，逻辑地址为2170B，求页号和页内偏移量。",
//            "24. 某磁盘转速为7200r/min，平均寻道时间为8ms，磁盘传输速率为40MB/s，读取一个4KB的扇区，计算平均访问时间。",
//            "25. 有4个进程到达时间和服务时间分别为：P1(0,5)、P2(1,3)、P3(2,2)、P4(3,4)，采用FCFS调度算法，计算平均周转时间。",
//            "26. 某文件系统采用混合索引组织方式，直接块10个，一级间接块1个，盘块大小1KB，盘块号占4B，求该文件系统支持的最大文件大小。",
//            "27. 页面访问序列为：1,2,3,4,1,2,5,1,2,3,4,5，内存分配3个物理块，分别采用FIFO和LRU页面置换算法，计算各自的缺页次数。",
//            "28. 某进程页表内容：第0页→5号物理块，第1页→10号物理块，第2页→4号物理块，第3页→7号物理块，页面大小为4KB，将逻辑地址0x1A5C转换为物理地址。",
//            "29. 信号量S初值为3，当前值为1，问有多少个等待进程？若当前值为-2，有多少个等待进程？",
//            "30. 某系统采用银行家算法，当前Available=(2,3,0)，某进程发出Request=(0,2,0)，该进程的Need=(7,4,3)，问系统能否将资源分配给它？请说明理由。",

            // ==================== 四、对比分析题（31-40）====================
            "31. 对比进程与线程，从调度、资源共享、系统开销、并发性四个方面说明它们的区别。",
            "32. 对比分页存储管理与分段存储管理方式，指出各自的优缺点和适用场景。",
            "33. 对比FCFS、SJF、RR、优先级调度这四种进程调度算法的优缺点。",
//            "34. 对比静态链接与动态链接，说明各自的特点和适用场景。",
//            "35. 对比硬实时任务与软实时任务，并各举一个例子说明。",
//            "36. 对比LRU页面置换算法与Clock页面置换算法，说明它们的实现原理和性能差异。",
//            "37. 对比中断驱动I/O方式与DMA方式，说明控制方式和CPU干预程度的不同。",
//            "38. 对比单级文件目录与树形目录，说明各自的优缺点。",
//            "39. 对比全虚拟化与半虚拟化，说明它们的实现方式差异和性能特点。",
//            "40. 对比对称多处理机与非对称多处理机，说明它们的体系结构差异。",

            // ==================== 五、综合论述题（41-50）====================
            "41. 结合进程同步知识，用记录型信号量机制完整实现生产者-消费者问题，要求写出伪代码并说明各信号量的含义和初值设置。",
            "42. 论述虚拟存储器实现的硬件支持和软件机制，详细描述缺页中断处理过程和地址变换流程。",
            "43. 结合读者-写者问题，论述如何利用信号量集机制解决该问题，并分析读者优先和写者优先两种策略的差异。"
//            "44. 论述银行家算法避免死锁的基本思想，并结合具体数据演示安全性检查算法的执行过程。",
//            "45. 论述多级反馈队列调度算法的调度机制，并分析它为何能较好地满足各类用户（终端型、短批处理型、长批处理型）的需求。",
//            "46. 论述文件系统中索引节点（iNode）的作用，以及在UNIX系统中混合索引组织方式的实现原理。",
//            "47. 论述从用户程序发出I/O请求到实际完成I/O操作的完整过程，说明各层I/O软件（用户层、设备无关层、设备驱动程序层、中断处理层）的功能。",
//            "48. 论述操作系统的微内核结构特点，分析其相对于传统模块化结构在可扩展性、可靠性和运行效率方面的优缺点。",
//            "49. 结合局部性原理，论述虚拟存储器的理论基础和工作原理，说明抖动产生的原因和常见的预防方法。",
//            "50. 论述SPOOLing系统的组成和工作原理，以打印机为例说明如何利用SPOOLing技术将独占设备改造为共享设备。"
    );

    @Autowired
    private MinerUtil minerUtil;
    @Autowired
    private TextUtil textUtil;
    private final RestClient restClient = RestClient.create();

    String local = "D:/Source/windows/Downloads/高血压.pdf";


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


        String s = minerUtil.uploadAndParse(mockFile);
        while (true) {
            MinerUtil.PollingResult zipUrl = minerUtil.getZipUrl(s);
            sleep(1000);
            if (zipUrl.isSuccess()) {
                List<String> zipUrls = zipUrl.getZipUrls();
                log.info("下载文件:{}", zipUrls);
                List<MinerUtil.ContentItem> contentItems = minerUtil.handleParseResult(zipUrls);
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
                break;
            }
        }
    }


    @Test
    void testRagAllQuestions() {
        // 1. 初始化 AI Agent（和你代码一致）
        Agent byId = agentService.getById(10);
        AiAgent aiAgent = new AiAgent(byId);
        Vector vector = vectorService.getById(7);
        MyVectorStore myVectorStore = new MyVectorStore(vector);
        aiAgent.addVectorStore(myVectorStore);

        // 2. 生成文件名
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filePath = "RAG_高血压测试问答_" + time + ".txt";

        // ====================== 总体统计数据 ======================
        List<QuestionStats> allStats = new ArrayList<>();
        long overallStartTime = System.currentTimeMillis();
        LocalDateTime overallStartDateTime = LocalDateTime.now();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            writer.println("========== 高血压 RAG 检索测试问答 ==========\n");
            writer.println("测试开始时间：" + overallStartDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            writer.println("总问题数：" + testQuestions.size());
            writer.println("\n==================================================\n");

            for (int i = 0; i < testQuestions.size(); i++) {
                String question = testQuestions.get(i);

                // 记录开始时间
                long startTime = System.currentTimeMillis();
                LocalDateTime startDateTime = LocalDateTime.now();

                System.out.println("\n【第 " + (i + 1) + " 题】");
                System.out.println("问题：" + question);
                System.out.println("开始时间：" + startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                System.out.println("回答：");

                writer.println("【第 " + (i + 1) + " 题】");
                writer.println("问题：" + question);
                writer.println("开始时间：" + startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                writer.print("回答：");

                // ====================== 核心改造：流式实时输出 ======================
                StringBuilder fullAnswer = new StringBuilder();

                // 记录首次响应时间
                final long[] firstResponseTime = {0};
                final boolean[] isFirstResponse = {true};


                aiAgent.chat(question)
                        .doOnNext(chunk -> {
                            // 记录首次响应时间（从开始到第一个字符）
                            if (isFirstResponse[0]) {
                                firstResponseTime[0] = System.currentTimeMillis() - startTime;
                                isFirstResponse[0] = false;
                            }
                            // 实时打印到控制台
                            System.out.print(chunk);
                            // 同时缓存完整答案
                            fullAnswer.append(chunk);
                        })
                        .blockLast(); // 等待流结束

                // 记录结束时间
                long endTime = System.currentTimeMillis();
                LocalDateTime endDateTime = LocalDateTime.now();

                // 计算耗时
                long totalDuration = endTime - startTime;
                int answerLength = fullAnswer.length();

                // 保存统计数据
                QuestionStats stats = new QuestionStats(
                        i + 1,
                        startDateTime,
                        endDateTime,
                        firstResponseTime[0],
                        totalDuration,
                        answerLength
                );
                allStats.add(stats);

                // 流结束后，把完整答案和统计信息写入文件
                writer.println(fullAnswer);
                writer.println("--------------------------------------------------");
                writer.println("【单题统计信息】");
                writer.println("开始时间：" + startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                writer.println("结束时间：" + endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                writer.println("首次响应时间：" + firstResponseTime[0] + " ms");
                writer.println("总耗时：" + totalDuration + " ms");
                writer.println("回答长度：" + answerLength + " 字符");
                writer.println("==================================================\n");

                // 控制台输出统计信息
                System.out.println("\n--------------------------------------------------");
                System.out.println("【单题统计信息】");
                System.out.println("开始时间：" + startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                System.out.println("结束时间：" + endDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
                System.out.println("首次响应时间：" + firstResponseTime[0] + " ms (从提问到第一个字符)");
                System.out.println("总耗时：" + totalDuration + " ms");
                System.out.println("回答长度：" + answerLength + " 字符");
                System.out.println("--------------------------------------------------");
            }

            // ====================== 总体统计汇总 ======================
            long overallEndTime = System.currentTimeMillis();
            LocalDateTime overallEndDateTime = LocalDateTime.now();
            long overallDuration = overallEndTime - overallStartTime;

            // 计算平均值和总值
            double avgFirstResponseTime = allStats.stream()
                    .mapToLong(QuestionStats::getFirstResponseTime)
                    .average()
                    .orElse(0);

            double avgTotalDuration = allStats.stream()
                    .mapToLong(QuestionStats::getTotalDuration)
                    .average()
                    .orElse(0);

            double avgAnswerLength = allStats.stream()
                    .mapToInt(QuestionStats::getAnswerLength)
                    .average()
                    .orElse(0);

            long totalFirstResponseTime = allStats.stream()
                    .mapToLong(QuestionStats::getFirstResponseTime)
                    .sum();

            long totalDurationSum = allStats.stream()
                    .mapToLong(QuestionStats::getTotalDuration)
                    .sum();

            int totalAnswerLength = allStats.stream()
                    .mapToInt(QuestionStats::getAnswerLength)
                    .sum();

            // 找出最快和最慢的响应
            QuestionStats fastestResponse = allStats.stream()
                    .min(Comparator.comparingLong(QuestionStats::getFirstResponseTime))
                    .orElse(null);

            QuestionStats slowestResponse = allStats.stream()
                    .max(Comparator.comparingLong(QuestionStats::getFirstResponseTime))
                    .orElse(null);

            QuestionStats shortestAnswer = allStats.stream()
                    .min(Comparator.comparingInt(QuestionStats::getAnswerLength))
                    .orElse(null);

            QuestionStats longestAnswer = allStats.stream()
                    .max(Comparator.comparingInt(QuestionStats::getAnswerLength))
                    .orElse(null);

            // 写入总体统计到文件
            writer.println("\n========== 总体统计报告 ==========\n");
            writer.println("【测试概览】");
            writer.println("总问题数：" + testQuestions.size());
            writer.println("测试开始时间：" + overallStartDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            writer.println("测试结束时间：" + overallEndDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            writer.println("测试总耗时：" + overallDuration + " ms (" + String.format("%.2f", overallDuration / 1000.0) + " 秒)");
            writer.println("\n【平均性能】");
            writer.println("平均首次响应时间：" + String.format("%.2f", avgFirstResponseTime) + " ms");
            writer.println("平均总耗时：" + String.format("%.2f", avgTotalDuration) + " ms");
            writer.println("平均回答长度：" + String.format("%.2f", avgAnswerLength) + " 字符");
            writer.println("\n【累计统计】");
            writer.println("累计首次响应时间：" + totalFirstResponseTime + " ms");
            writer.println("累计总耗时：" + totalDurationSum + " ms");
            writer.println("累计回答长度：" + totalAnswerLength + " 字符");
            writer.println("\n【极值分析】");
            if (fastestResponse != null) {
                writer.println("最快首次响应：第 " + fastestResponse.getQuestionNumber() + " 题 - " + fastestResponse.getFirstResponseTime() + " ms");
            }
            if (slowestResponse != null) {
                writer.println("最慢首次响应：第 " + slowestResponse.getQuestionNumber() + " 题 - " + slowestResponse.getFirstResponseTime() + " ms");
            }
            if (shortestAnswer != null) {
                writer.println("最短回答：第 " + shortestAnswer.getQuestionNumber() + " 题 - " + shortestAnswer.getAnswerLength() + " 字符");
            }
            if (longestAnswer != null) {
                writer.println("最长回答：第 " + longestAnswer.getQuestionNumber() + " 题 - " + longestAnswer.getAnswerLength() + " 字符");
            }
            writer.println("\n【详细数据】");
            writer.println("题号\t首次响应(ms)\t总耗时(ms)\t回答长度(字符)");
            for (QuestionStats stat : allStats) {
                writer.println(stat.getQuestionNumber() + "\t" +
                        stat.getFirstResponseTime() + "\t\t" +
                        stat.getTotalDuration() + "\t\t" +
                        stat.getAnswerLength());
            }
            writer.println("\n==================================================\n");

            // 控制台输出总体统计
            System.out.println("\n\n================== 总体统计报告 ==================");
            System.out.println("【测试概览】");
            System.out.println("总问题数：" + testQuestions.size());
            System.out.println("测试总耗时：" + overallDuration + " ms (" + String.format("%.2f", overallDuration / 1000.0) + " 秒)");
            System.out.println("\n【平均性能】");
            System.out.println("平均首次响应时间：" + String.format("%.2f", avgFirstResponseTime) + " ms");
            System.out.println("平均总耗时：" + String.format("%.2f", avgTotalDuration) + " ms");
            System.out.println("平均回答长度：" + String.format("%.2f", avgAnswerLength) + " 字符");
            System.out.println("\n【累计统计】");
            System.out.println("累计首次响应时间：" + totalFirstResponseTime + " ms");
            System.out.println("累计总耗时：" + totalDurationSum + " ms");
            System.out.println("累计回答长度：" + totalAnswerLength + " 字符");
            System.out.println("\n【极值分析】");
            if (fastestResponse != null) {
                System.out.println("⚡ 最快首次响应：第 " + fastestResponse.getQuestionNumber() + " 题 - " + fastestResponse.getFirstResponseTime() + " ms");
            }
            if (slowestResponse != null) {
                System.out.println("🐌 最慢首次响应：第 " + slowestResponse.getQuestionNumber() + " 题 - " + slowestResponse.getFirstResponseTime() + " ms");
            }
            if (shortestAnswer != null) {
                System.out.println("📝 最短回答：第 " + shortestAnswer.getQuestionNumber() + " 题 - " + shortestAnswer.getAnswerLength() + " 字符");
            }
            if (longestAnswer != null) {
                System.out.println("📚 最长回答：第 " + longestAnswer.getQuestionNumber() + " 题 - " + longestAnswer.getAnswerLength() + " 字符");
            }

            // 计算生成速度（字符/秒）
            double avgGenerationSpeed = avgAnswerLength / (avgTotalDuration / 1000.0);
            System.out.println("\n【生成速度】");
            System.out.println("平均生成速度：" + String.format("%.2f", avgGenerationSpeed) + " 字符/秒");

            System.out.println("\n✅ 所有测试完成！问答已写入文件：" + filePath);
            System.out.println("📊 总体统计报告已生成，包含平均性能、极值分析和详细数据");

        } catch (IOException e) {
            System.err.println("❌ 文件写入失败：" + e.getMessage());
        }
    }

    // ====================== 内部类：问题统计数据 ======================
    private static class QuestionStats {
        private final int questionNumber;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final long firstResponseTime;
        private final long totalDuration;
        private final int answerLength;

        public QuestionStats(int questionNumber, LocalDateTime startTime, LocalDateTime endTime,
                             long firstResponseTime, long totalDuration, int answerLength) {
            this.questionNumber = questionNumber;
            this.startTime = startTime;
            this.endTime = endTime;
            this.firstResponseTime = firstResponseTime;
            this.totalDuration = totalDuration;
            this.answerLength = answerLength;
        }

        public int getQuestionNumber() {
            return questionNumber;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public long getFirstResponseTime() {
            return firstResponseTime;
        }

        public long getTotalDuration() {
            return totalDuration;
        }

        public int getAnswerLength() {
            return answerLength;
        }
    }

    @Test
    public void chatWithImage() {
        String API_KEY = "sk-1ff40835f4a14e39970c2bbfacde8156";
        String API_URL = "https://dashscope.aliyuncs.com/compatible-mode";
        String MODEL = "qwen-plus";

        Agent byId = agentService.getById(10);
        AiAgent aiAgent = new AiAgent(byId);

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


        MyQueryExpander queryExpander = MyQueryExpander.builder()
                .chatClient(chatClient)
                .build();

        // 记录总时间
        long totalTime = 0;
        List<Query> results = null;


// 准备多个历史消息对（模拟长对话历史）
        List<AbstractMessage> historyMessages = new ArrayList<>();

// 添加多轮历史对话
        historyMessages.add(new UserMessage("41. 结合进程同步知识，用记录型信号量机制完整实现生产者-消费者问题，要求写出伪代码并说明各信号量的含义和初值设置。"));
        historyMessages.add(new AssistantMessage("以下依据上下文，使用**记录型信号量机制**完整实现生产者-消费者问题：\n\n---\n\n### 一、问题设定（基于上下文）  \n设缓冲池具有 $ n $ 个大小相同的缓冲区（即循环缓冲区），生产者进程不断生产产品并放入空缓冲区，消费者进程不断从满缓冲区中取走产品消费 <ID>49_256</ID><ID>49_236</ID>。需保证：  \n- 缓冲池的访问是**互斥**的（防止多个进程同时修改同一缓冲区）；  \n- 生产者与消费者之间存在**同步**关系：  \n  - 生产者不能向**已满**的缓冲池投放产品；  \n  - 消费者不能从**为空**的缓冲池取走产品 <ID>49_236</ID>。\n\n---\n\n### 二、信号量定义、含义与初值  \n\n根据上下文明确说明：  \n- **`mutex`**：互斥信号量，用于实现对**缓冲池（共享数据结构）的互斥访问**；其初值为 **1**，表示允许一个进程进入临界区 <ID>49_249</ID><ID>49_256</ID>。  \n- **`empty`**：资源信号量，表示**当前空缓冲区的数量**；初值为 **$ n $**（缓冲池初始全空）<ID>49_256</ID>。  \n- **`full`**：资源信号量，表示**当前满缓冲区的数量**；初值为 **0**（缓冲池初始无产品）<ID>49_256</ID>。  \n\n> 注：上下文明确指出，\"利用互斥信号量 `mutex` 实现各进程对缓冲池的互斥使用；利用信号量 `empty` 和 `full` 分别表示缓冲池中空缓冲区和满缓冲区的数量\"，且\"`empty` 初值为 $ n $，`full` 初值为 0\" <ID>49_256</ID>；`mutex` 初值为 1 是记录型信号量作为互斥信号量的标准设置 <ID>49_246</ID><ID>49_249</ID>。\n\n---\n\n### 三、伪代码实现（含完整 P/V 操作）  \n\n**全局变量声明：**  \n```plaintext\n// 缓冲池：n 个单元的数组 buffer[0..n-1]\nint in = 0;      // 生产者写入指针（输入指针）\nint out = 0;     // 消费者读出指针（输出指针）\n// 注意：in 和 out 均按模 n 循环使用，即 (in+1)%n, (out+1)%n <ID>49_237</ID>\n```\n\n**信号量声明与初始化：**  \n```plaintext\nsemaphore mutex = 1;     // 互斥访问缓冲池\nsemaphore empty = n;     // 空缓冲区数目，初值为 n\nsemaphore full = 0;      // 满缓冲区数目，初值为 0\n```\n\n**生产者进程（Producer）：**  \n```plaintext\nwhile (true) {\n    nextp = produce();           // 生产一个产品\n    \n    wait(empty);                 // 申请一个空缓冲区（若无则阻塞）\n    wait(mutex);                 // 进入临界区（互斥访问缓冲池）\n    \n    buffer[in] = nextp;          // 将产品放入缓冲区\n    in = (in + 1) % n;           // 移动输入指针\n    \n    signal(mutex);               // 离开临界区\n    signal(full);                // 告知：新增一个满缓冲区\n}\n```\n\n**消费者进程（Consumer）：**  \n```plaintext\nwhile (true) {\n    wait(full);                  // 申请一个满缓冲区（若无则阻塞）\n    wait(mutex);                 // 进入临界区（互斥访问缓冲池）\n    \n    nextc = buffer[out];         // 从缓冲区取出产品\n    out = (out + 1) % n;         // 移动输出指针\n    \n    signal(mutex);               // 离开临界区\n    signal(empty);               // 告知：新增一个空缓冲区\n}\n```\n\n> ✅ **操作顺序依据**：上下文强调，\"每个程序中的多个 `wait` 操作的顺序不能颠倒，应先执行对资源信号量的 `wait` 操作，再执行对互斥信号量的 `wait` 操作，否则可能会引起进程死锁\" <ID>49_256</ID>。因此，`wait(empty)`/`wait(full)` 必须在 `wait(mutex)` 之前。\n\n> ✅ **原子性保障**：`wait` 和 `signal` 是原子操作，不可中断，确保信号量 `value` 修改与进程阻塞/唤醒的正确性 <ID>49_246</ID>。\n\n---\n\n### 四、关键说明（基于上下文）  \n\n- 所有 `wait` 和 `signal` 操作必须**成对出现**：缺少 `wait(mutex)` 导致无法互斥；缺少 `signal(mutex)` 将使临界资源永远不被释放，其他进程永久阻塞 <ID>49_250</ID>；同理，`empty`/`full` 的 `wait`/`signal` 也必须成对，分别位于不同进程中以实现同步 <ID>49_256</ID>。  \n- 该方案使用的是**记录型信号量**，因其能避免\"忙等\"，遵循\"让权等待\"准则，当 `wait(S)` 中 `S.value ≤ 0` 时会将进程阻塞并挂入链表，而非循环测试 <ID>49_246</ID>。  \n- 此实现适用于**多个生产者与多个消费者并发运行**的场景，具有代表性与实用价值 <ID>49_255</ID><ID>49_256</ID>。\n\n---  \n✅ 综上，本解答严格依据上下文所给定义、初值、操作逻辑与约束条件完成。"));

// 添加第二组历史（操作系统其他知识点）
        historyMessages.add(new UserMessage("请解释虚拟内存中的页面置换算法，并比较FIFO、LRU和Clock算法的优缺点"));
        historyMessages.add(new AssistantMessage("## 页面置换算法详解\n\n### 1. FIFO（先进先出）算法\n**原理**：选择在内存中驻留时间最长的页面进行置换。\n\n**实现**：使用队列维护页面进入顺序。\n\n**优点**：\n- 实现简单，开销小\n- 公平性强\n\n**缺点**：\n- 可能出现Belady异常（分配帧数增加，缺页率反而上升）\n- 性能较差，不考虑页面使用频率\n\n### 2. LRU（最近最久未使用）算法\n**原理**：选择最近最长时间未被访问的页面进行置换。\n\n**实现**：使用栈或计数器记录访问时间。\n\n**优点**：\n- 性能较好，符合局部性原理\n- 缺页率较低\n\n**缺点**：\n- 硬件支持成本高\n- 实现复杂，需要维护访问历史\n\n### 3. Clock算法（NRU近似LRU）\n**原理**：使用循环扫描和访问位（使用位）来近似LRU。\n\n**实现**：维护环形链表和指针，利用访问位进行选择。\n\n**优点**：\n- 开销较小，只需维护访问位\n- 性能接近LRU\n- 硬件支持简单\n\n**缺点**：\n- 只是LRU的近似，不是最优\n- 需要定期清零访问位\n\n### 性能对比总结\n- **最佳性能**：LRU > Clock > FIFO\n- **实现复杂度**：LRU > Clock > FIFO\n- **硬件开销**：LRU > Clock > FIFO\n\n现代操作系统通常采用改进的Clock算法（如增强型Clock算法，同时考虑访问位和修改位）。"));

// 添加第三组历史（文件系统）
        historyMessages.add(new UserMessage("详细说明Unix/Linux中的inode文件系统结构，包括文件存储方式和目录实现"));
        historyMessages.add(new AssistantMessage("## Unix/Linux inode文件系统详解\n\n### 一、inode结构\ninode（索引节点）是文件系统的核心数据结构，包含文件的元数据：\n\n**主要字段**：\n- 文件类型和权限（mode）\n- 文件链接数（nlink）\n- 文件所有者ID（uid）\n- 文件所属组ID（gid）\n- 文件大小（size）\n- 访问时间（atime）\n- 修改时间（mtime）\n- 状态改变时间（ctime）\n- 数据块指针数组（15个指针）\n\n### 二、数据块寻址方式\n\n**直接指针**（12个）：\n- 直接指向数据块，每个4KB，共可寻址48KB\n\n**间接指针**：\n- **一级间接指针**：指向一个索引块，每个索引块包含1024个指针，可寻址4MB\n- **二级间接指针**：指向一级间接块，可寻址4GB\n- **三级间接指针**：指向二级间接块，可寻址4TB\n\n### 三、目录实现\n\n**目录文件结构**：\n```plaintext\ndirent结构：\n- inode号（4字节）\n- 文件名长度（2字节）\n- 文件类型（1字节）\n- 文件名（变长）\n```\n\n**查找过程**：\n1. 解析路径，从根目录开始\n2. 在目录文件中线性查找文件名\n3. 获取inode号\n4. 从inode表读取inode\n5. 根据inode中的指针访问数据\n\n### 四、优点与缺点\n\n**优点**：\n- 支持硬链接（多个目录项指向同一inode）\n- 文件名与文件属性分离，便于管理\n- 支持大文件（通过间接指针）\n- 空间利用率高\n\n**缺点**：\n- 小文件开销较大（每个文件至少占用一个inode）\n- inode数量有限，可能不够用\n- 磁盘碎片问题\n\n### 五、示例：读取文件的完整流程\n```\nopen(\"/home/user/file.txt\")\n→ 读取根目录inode（固定位置）\n→ 查找\"home\"目录项\n→ 读取home目录inode\n→ 查找\"user\"目录项\n→ 读取user目录inode\n→ 查找\"file.txt\"目录项\n→ 获取文件inode号\n→ 读取文件inode到内存\n→ 根据inode中的指针读取数据块\n```"));

// 循环50次
        for (int i = 0; i < 50; i++) {
            long startTime = System.nanoTime(); // 开始计时

            // 执行查询扩展
            List<Query> expand = queryExpander.expand(Query.builder()
//                            .text("这个儿童也会得吗？")
//                            .history(
//                                    new UserMessage("高血脂要怎么降"),
//                                    new AssistantMessage("低脂饮食+运动+必要时用药...")
//                            )
                            .text("阐述历史聊天所涉及的知识")
                            .history(historyMessages.getFirst(), historyMessages.get(1),
                                    historyMessages.get(2), historyMessages.get(3),
                                    historyMessages.get(4), historyMessages.get(5))
                            .build()
            );

            long endTime = System.nanoTime(); // 结束计时
            long duration = endTime - startTime;
            totalTime += duration;

            // 保存最后一次的结果
            if (i == 49) {
                results = expand;
            }

            // 打印每次执行时间（可选）
            System.out.printf("第%d次执行耗时: %.3f ms%n", (i + 1), duration / 1_000_000.0);
        }

// 输出统计信息
        System.out.println("========== 统计结果 ==========");
        System.out.printf("总耗时: %.3f ms%n", totalTime / 1_000_000.0);
        System.out.printf("平均耗时: %.3f ms%n", (totalTime / 50.0) / 1_000_000.0);
        System.out.printf("最大/最小耗时: 可通过添加逻辑获取%n");
        System.out.println("==============================");




    }

}