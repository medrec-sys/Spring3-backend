package fun.medrec.spring.utils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ModelUtil {
    private static final String SUMMARY_TEMPLATE = """
            # 角色
            你是知识库分片总结助手。请对下面的文本进行**精简、客观、关键词密集**的总结，用于 RAG 检索召回。
            
            # 目标
            生成的摘要需要作为该分片的“检索锚点”，帮助用户问题快速命中相关内容。摘要应最大限度地保留关键信息，同时控制在极小的token消耗内。
            
            # 文本
            【上文】
            %s

            【下文】
            %s

            【输入片段】
            %s

            # 注意
            上下文作为参考，用于理解片段含义，总结中一定不可出现仅在上下文而未在片段中出现的内容。
            文本片段包含标题层次结构信息，可作为理解依据。

            【约束】
            1. 只保留核心事实、定义、数据、结论、关键主体
            2. 去掉冗余描述、口语化表达
            3. 长度为原文的 0.2~0.4
            4. 不要主观推断，不要补充额外信息
            5. 语句通顺，便于向量检索匹配
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
                .system(ModelUtil.SUMMARY_TEMPLATE)
                .user(String.format(SUMMARY_TEMPLATE, context_before, context_after, text))
                .options(ModelUtil.summaryOptions)
                .call()
                .content();
    }

}
