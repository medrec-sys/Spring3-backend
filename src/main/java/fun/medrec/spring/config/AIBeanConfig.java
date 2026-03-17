package fun.medrec.spring.config;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AIBeanConfig {
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;
    @Value("${spring.data.redis.password}")
    private String password;
    @Bean
    public JedisPooled jedisPooled() {
        System.out.println("host:"+host);
        System.out.println("port:"+port);
        return new JedisPooled(host, port, null, password);
    }
    @Value("${spring.ai.vectorstore.redis.index-name}")
    private String indexName;
    @Value("${spring.ai.vectorstore.redis.initialize-schema}")
    private Boolean schema;
    @Value("${spring.ai.vectorstore.redis.prefix}")
    private String prefix;


    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, OpenAiEmbeddingModel model) {
        return RedisVectorStore.builder(jedisPooled,model)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(schema)
                .build();
    }
}
