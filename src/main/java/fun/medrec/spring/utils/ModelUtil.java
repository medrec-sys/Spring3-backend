package fun.medrec.spring.utils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

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

    private static final String SUMMARY_AGGREGATE_TEMPLATE = """
           # 角色
           你是知识库树形结构的摘要聚合助手。将多个子节点的摘要**合并提炼**，生成父节点的检索锚点。
    
           # 输入
           以下是同一父节点下所有子节点的摘要（每个摘要已是对原始内容的精炼）：
    
           %s
    
           # 约束
           1. **去重合并**：合并相同/相似信息，只提取子节点共同提到的东西
           2. **保真**：不添加任何子节点摘要中未提及的信息
           3. **长度**：控制在输入总长度的30%-50%（因为输入已是精炼内容）
           4. **结构**：若子节点摘要之间存在逻辑顺序（如时间、流程），可适度保留
           5. **风格**：语句通顺，关键词密集，便于检索召回
    
           # 输出
           仅输出合并后的摘要文本，无需额外说明。
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

    // 总结段落组
    public static String aggregateSummaries(List<String> childSummaries) {
        // 构建输入文本块
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < childSummaries.size(); i++) {
            sb.append("【子节点").append(i + 1).append("】\n")
                    .append(childSummaries.get(i))
                    .append("\n\n");
        }

        // 调用AI聚合摘要
        return ModelUtil.chatClient
                .prompt()
                .user(String.format(SUMMARY_AGGREGATE_TEMPLATE, sb))
                .options(ModelUtil.summaryOptions)
                .call()
                .content();
    }

}
