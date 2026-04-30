package fun.medrec.spring.Ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.Chat;
import fun.medrec.spring.utils.ModelUtil;
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
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class AiAgent {
    private static String modelName;
    private static OpenAiApi openAiApi;
    private static JdbcTemplate jdbcTemplate;
    private static final String SEARCH_TEMPLATE = """
            用户问题：{query}
            
            相关上下文（每段内容开头 <ID>数字</ID> 是文档唯一ID）：
            ---------------------
            {context}
            ---------------------
            
            回答规则：
            -- 内容要求
            1. 严格基于上述上下文信息回答，不要使用你的先验知识。
            2. 如果上下文包含直接答案，请简洁、准确地回答。
            3. 如果上下文不包含直接答案，但包含相关、可推断的信息，请进行合理的逻辑整合与推断，并在回答中明确说明推断依据。
            4. 如果上下文完全没有相关信息，请直接说明“上下文中未提供相关信息”，并简要列出上下文实际覆盖的主要内容范围。
            5. 回答要结构清晰、客观准确。
            
            -- 返回的参考文档格式要求
            1. 回答中**每一句话**只要使用了上下文内容，必须在句尾**直接标注对应的文档ID**，格式： <ID>id</ID>。
               示例：AI生成技术正在快速发展 <ID>1001</ID>，在多领域得到应用<ID>1002</ID>。
            2. 引用ID必须紧跟在对应句子后面，**不要统一放在最后**。
            3. 如果一句话用到多个文档，可同时标注：<ID>1006</ID><ID>1005</ID>。
            4. 不编造ID，不编造内容，不添加无关信息。
            5. 重复的id前面引用过就不要引用
            
            请直接开始回答：
            """;
    public final MyQueryExpander ex;

    private ChatClient client;
    private static ModelUtil modelUtil;
    private final Agent agent;
    private final List<MyVectorStore> stores = new ArrayList<>();
    @Getter
    @Setter
    private List<Document> documents = new ArrayList<>();

    @Getter
    private static Cache<Integer, AiAgent> agentCache;

    public static void init(JdbcTemplate jdbcTemplate, String baseUrl, String apiKey, String modelName, ModelUtil modelUtil) {
        AiAgent.modelName = modelName;
        AiAgent.jdbcTemplate = jdbcTemplate;

        RestClient.Builder customRestClientBuilder = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.simple()
                        .build(ClientHttpRequestFactorySettings.defaults()
                                .withReadTimeout(Duration.ofSeconds(3000))));


        AiAgent.openAiApi = OpenAiApi.builder()
                .restClientBuilder(customRestClientBuilder)
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();


        AiAgent.agentCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();

        AiAgent.modelUtil = modelUtil;
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

        // 配置

        PromptTemplate searchPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .template(SEARCH_TEMPLATE)
                .build();

        MyQueryExpander expander = MyQueryExpander.builder()
                .modelUtils(modelUtil)
                .build();
        this.ex = expander;


        // 文档查询器
        MultiVectorStoreDocumentRetriever documentRetriever = MultiVectorStoreDocumentRetriever.builder()
                .vectorStores(stores)
                .topK(agent.getTopK())
                .similarityThreshold(agent.getSimilarity())
                .build();
        // 查询增强器，用于将检索到的文档内容整合到用户查询中
        MyContextualQueryAugmenter augmenter = MyContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                // 自定义文本应该如何嵌入查询条件里，这里包含文档内容和文档id，用于使ai生成文本使标记文档引用id
                .documentFormatter(
                        docs -> docs.stream().map(
                                document -> {
                                    StringBuilder s = new StringBuilder();
                                    String id = document.getMetadata().get("id").toString();
                                    // 核心：用 <ID>id</ID> 格式，模型才会在回答里自动标记
                                    s.append("<ID>").append(id).append("</ID>").append(document.getText()).append("\n");
                                    return s.toString();
                                }
                        ).collect(Collectors.joining(System.lineSeparator()))
                )
                .promptTemplate(searchPromptTemplate)
                .build();
        // 检索后处理器
        MyDocumentPostProcessor myDocumentPostProcessor = new MyDocumentPostProcessor();
        // 配置RetrievalAugmentationAdvisor
        Advisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .documentPostProcessors(myDocumentPostProcessor)
                .queryAugmenter(augmenter)
                .queryExpander(expander)
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
                                .param("topK", agent.getTopK())
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

    public List<Chat> getHistory(Integer id) {
        String sql = "SELECT * FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?";

        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Chat.class), id);
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