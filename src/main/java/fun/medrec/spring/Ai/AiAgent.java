package fun.medrec.spring.Ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.entity.Agent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AiAgent {
    private static String modelName;
    private static OpenAiApi openAiApi;
    private static JdbcTemplate jdbcTemplate;
    private static final String SEARCH_TEMPLATE = """
			用户问题：{query}

			相关上下文：
			---------------------
			{context}
			---------------------

			回答规则：
			1. 严格基于上述上下文信息回答
			2. 不要使用你的先验知识
			3. 如果上下文中没有相关信息，请回复："抱歉，根据当前知识库，我无法回答这个问题"
			4. 回答要简洁、准确

			请回答：
			""";
    private static final String REWRITER_TEMPLATE = """
			给定用户查询，将其重写以在查询{target}时获得更好的结果。
			移除任何无关信息，并确保查询简洁且具体。

			原始查询：
			{query}

			重写后的查询：
			""";

    private static final String COMPRESSION_TEMPLATE = """
			请将新问题与对话历史结合，生成一个完整的独立查询。
			
			示例：
			历史：用户问"北京天气怎么样？"
			新问题："明天呢？"
			独立查询："北京明天天气怎么样？"
			
			现在请处理：
			对话历史：{history}
			新问题：{query}
			
			独立查询：
			""";

    private ChatClient client;
    private final Agent agent;
    private final List<MyVectorStore> stores = new ArrayList<>();
    @Getter
    @Setter
    private List<Document> documents = new ArrayList<>();

    @Getter
    private static Cache<Integer, AiAgent> agentCache;

    public static void init(JdbcTemplate jdbcTemplate, String baseUrl, String apiKey, String modelName) {
        AiAgent.modelName = modelName;
        AiAgent.jdbcTemplate = jdbcTemplate;
        AiAgent.openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        AiAgent.agentCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    public static AiAgent getAgent(Integer id) {
        return agentCache.getIfPresent(id);
    }

    public static void deleteAgent(Integer id) {
        agentCache.invalidate(id);
    }

    public static void reBuild(AiAgent agent) {
        agent.client = new AiAgent(agent.agent, agent.stores).client;
    }

    public AiAgent(Agent agent) {
        this(agent, new ArrayList<>());
    }

    public AiAgent(Agent agent, List<MyVectorStore> stores) {
        this.agent = agent;
        // 配置模型
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(agent.getTemperature())
                .maxTokens(10000)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(AiAgent.openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();
        ChatClient.Builder builder = ChatClient
                .builder(model);

        // 用于重写用户查询的model，使用户的问题更精准
        ChatClient.Builder rewriterBuilder = ChatClient.builder(model);
        // 用于压缩对话历史的model
        ChatClient.Builder compressionQueryBuilder = ChatClient.builder(model);

        // 配置记忆
        ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(agent.getMaxMessage())
                .build();
        builder.defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        );

        // 配置文档返回
        DocumentAdvisor documentAdvisor = new DocumentAdvisor(this);
        builder.defaultAdvisors(documentAdvisor);

        // 配置对话重写和历史消息压缩
        PromptTemplate rewriterPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .template(REWRITER_TEMPLATE)
                .build();
        PromptTemplate compressionPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .template(COMPRESSION_TEMPLATE)
                .build();
        RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(rewriterBuilder.build().mutate())
                .promptTemplate(rewriterPromptTemplate)
                .build();
        CompressionQueryTransformer compressionTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(compressionQueryBuilder.build().mutate())
                .promptTemplate(compressionPromptTemplate)
                .build();

        MultiVectorStoreDocumentRetriever documentRetriever = MultiVectorStoreDocumentRetriever.builder()
                .vectorStores(stores)
                .topK(agent.getTopK())
                .similarityThreshold(agent.getSimilarity())
                .build();
        PromptTemplate searchPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .template(SEARCH_TEMPLATE)
                .build();
        ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder().promptTemplate(searchPromptTemplate).build();

        // 配置RetrievalAugmentationAdvisor
        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(augmenter)
                .queryTransformers(transformer, compressionTransformer)
                .build();
        builder.defaultAdvisors(retrievalAugmentationAdvisor);
        client = builder.build();
        AiAgent.agentCache.put(agent.getId(), this);
    }

    public Flux<String> chat(String sentence) {
        return client
                .prompt()
                .system(agent.getPrompt())
                .user(sentence)
                .advisors(
                a -> a.param(ChatMemory.CONVERSATION_ID, agent.getId())
        )
                .stream()
                .content();
    }

    public void delete(int id) {
        String sql = "delete from SPRING_AI_CHAT_MEMORY where id = ? and conversation_id = ?";
        jdbcTemplate.update(sql, id, agent.getId());
    }

    public void deleteAll() {
        String sql = "delete from SPRING_AI_CHAT_MEMORY where conversation_id = ?";
        jdbcTemplate.update(sql, agent.getId() + "");

    }

    public void addVectorStore(MyVectorStore vectorStore) {
        stores.add(vectorStore);
        reBuild(this);
    }

    public void deleteVectorStore(int id) {
        stores.removeIf(store -> store.getVector().getId() == id);
        reBuild(this);
    }
}
