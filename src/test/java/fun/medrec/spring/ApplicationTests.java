package fun.medrec.spring;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.MinerUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
}