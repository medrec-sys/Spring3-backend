package fun.medrec.spring.utils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ModelUtil {
    private static final String SUMMARY_TEMPLATE = """
            # 角色
            你是知识库分片总结助手。对原始文本进行**精炼、客观、关键词密集**的总结，作为该分片的检索锚点。
            
            # 要求
            先使用简单的一段话总结的文本大概意思
            在列举出关键主体

            # 上下文（仅用于理解，不写入总结）
            【上文】%s
            【下文】%s

            # 待总结文本(段落的层次结构用于理解，不写入总结)
            %s

            # 约束
            1. **范围**：总结仅基于【待总结文本】，上文/下文仅辅助理解，绝不引入外部信息
            2. **长度**：控制在原文的0.2-0.4（若原文极短，可接近1.0）
            3. **风格**：语句通顺，便于向量检索匹配；避免主观推断和补充解释
            """;


    private static final Double summary_temperature = 0.2;

    private static ChatClient chatClient;
    private static OpenAiChatOptions summaryOptions;

    private ModelUtil() {
        throw new AssertionError();
    }

    public static void init(String baseUrl, String apiKey, String modelName) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        summaryOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(summary_temperature)
                .maxTokens(10000)
                .build();


        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .build();

        ModelUtil.chatClient = ChatClient.builder(model)
                .build();
    }


    public static String summarizer(String text, String context_before, String context_after) {
        return ModelUtil.chatClient
                .prompt()
                .user(String.format(SUMMARY_TEMPLATE, context_before, context_after, text))
                .options(ModelUtil.summaryOptions)
                .call()
                .content();
    }
}
