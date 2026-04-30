package fun.medrec.spring.utils;

import fun.medrec.spring.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ModelUtil {

    private static final String QUERY_TEMPLATE = """
        你是RAG检索优化专家。
        
        任务：
        1. 根据用户当前问题 + 对话历史，理解用户真正想查询的完整意图。
        2. 生成 {number} 个**完整、通顺、适合向量检索**的查询句子。
        
        输入：
        - 用户问题：{query}
        - 对话历史：{history}
        - 生成数量：{number}
        
        严格遵守以下规则：
        1. 必须是**完整通顺的查询句子**，不要只输出关键词。
        2. 句子简短明确，符合用户真实提问方式，适合向量检索。
        3. 不描述过程，不冗余，不解释。
        4. 友好问题（你好、你是谁）直接返回原问题。
        5. 输出：每行一个变体，无序号、无多余内容。
        
        现在输出变体：
        """;

    private final PromptTemplate promptTemplate = new PromptTemplate(QUERY_TEMPLATE);


    private final ChatClient imageClient;
    private final ChatClient searchClient;

    public ModelUtil(
            @Value("${spring.ai.openai.chat.options.model}") String modelName,
            @Value("${spring.ai.openai.chat.options.vlm}") String vlmName,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl
    ) {
        RestClient.Builder customRestClientBuilder = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withReadTimeout(Duration.ofSeconds(3000))));

        OpenAiApi openAiApi = OpenAiApi.builder()
                .restClientBuilder(customRestClientBuilder)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        OpenAiChatOptions imageOptions = OpenAiChatOptions.builder()
                .model(vlmName)
                .temperature(0.2)
                .maxTokens(10000)
                .build();

        OpenAiChatOptions searchOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.2)
                .maxTokens(10000)
                .build();



        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();

        imageClient = ChatClient.builder(model)
                .defaultOptions(imageOptions)
                .build();

        searchClient = ChatClient.builder(model)
                .defaultOptions(searchOptions)
                .build();
    }

    /**
     * @param text 图片基础信息
     * @param file 图片文件
     * @description 描述图片
     */
    public String pictureDescriber(String text, MultipartFile file) {
        log.debug("图片描述开始:{}", text);
        try {
            ByteArrayResource byteArrayResource = new ByteArrayResource(file.getBytes());
            String PICTURE_TEMPLATE = """
                    你是一个专业的图片描述专家，擅长将图片内容转化为精确、结构化的文本描述，适用于知识检索和语义搜索。
                    
                    请按以下格式输出图片描述：
                    
                    [图片类型]: 照片/截图/图表/插画/文档/其他
                    
                    [核心内容]: 用1-2句话概括图片最核心的信息
                    
                    [详细描述]:
                    - 主体对象：图片中的主要人物/物体/场景
                    - 关键细节：颜色、数量、位置、状态等可检索的关键信息
                    - 文本内容：图片中出现的任何文字、数字、标签（如有）
                    
                    [关键词]: 提供5-10个可用于检索的关键词，用逗号分隔
                    
                    [适用场景]: 这张图片可能涉及的业务领域或使用场景
                    
                    要求：
                    1. 客观描述，不要添加主观评价或情感色彩
                    2. 优先提取可被搜索的关键信息（名称、数字、属性等）
                    3. 如果是数据图表，重点描述趋势、数值、对比关系
                    4. 如果是截图/文档，完整提取其中的关键文字信息
                    5. 输出长度控制在200-400字之间，便于存储和检索
                    6. 使用中文输出
                    """;
            return imageClient
                    .prompt()
                    .system(PICTURE_TEMPLATE)
                    .user(userSpec -> userSpec
                            .text("请描述这张图片的内容" + (text.isEmpty() ? "" : "(图片概述：" + text + ")"))
                            .media(MimeTypeUtils.IMAGE_JPEG, byteArrayResource)
                    )
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("图片描述失败", e);
            throw new BusinessException("图片描述失败");
        }
    }

    private String formatConversationHistory(List<Message> history) {
        if (history.isEmpty()) {
            return "";
        }

        return history.stream()
                .filter(message -> message.getMessageType().equals(MessageType.USER)
                        || message.getMessageType().equals(MessageType.ASSISTANT))
                .map(message -> "%s: %s".formatted(message.getMessageType(), message.getText()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * @description 构建扩展查询模板
     */
    private String buildQueryTemplate(String text,  List<Message> history, Integer n) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("query", text);
        vars.put("history", formatConversationHistory(history));
        vars.put("number", n);
        return promptTemplate.render(vars);
    }

    public String search(Query query, Integer n) {
        log.debug("扩展查询开始:{}", query.text());
        String s = buildQueryTemplate(query.text(), query.history(), n);
        String content = searchClient
                .prompt()
                .user(s)
                .call()
                .content();
        log.debug("完成");
        return content;
    }

    public Flux<String> searchWithFlux(String text,  List<Message> history, Integer n) {
        log.debug("FLux:扩展查询开始:{}", text);
        String s = buildQueryTemplate(text, history, n);
        return searchClient
                .prompt()
                .user(s)
                .stream()
                .content();
    }

}
