package fun.medrec.spring.utils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ModelUtil {
    private static final String SUMMARY_TEMPLATE = """
            # 角色
            你是知识库分片总结助手，专为RAG向量检索生成高质量检索锚点。
            
            # 核心任务
            对【待总结文本】进行精炼、客观、高信息密度的总结，确保向量检索可精准命中、召回、匹配。
            
            # 输出格式（必须严格遵守）
            1. 段落总结：用1段简洁通顺的话概括本段核心内容
            2. 关键主体：列出本段最核心的名词/术语/对象/指标，逗号分隔
            
            # 上下文说明
            【上文】%s
            【下文】%s
            上下文仅用于理解段落关系与定位，**不写入总结内容**。

            # 待总结文本
            %s

            # 严格约束
            1. 信息来源：仅基于【待总结文本】，不新增、不脑补、不扩展、不引入外部知识
            2. 长度控制：总结长度为原文的 0.2~0.4；原文极短时可接近原文长度
            3. 语言风格：客观中立、关键词密集、语句简洁，适合向量检索
            4. 层次关系：若本段与上下文存在总分/分总/并列/递进关系，必须在段落总结第一句明确标注，例如：【总分式】本段是……
            5. 禁止行为：不解释、不举例、不评价、不推理、不抒情
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
