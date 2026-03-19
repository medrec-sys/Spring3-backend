package fun.medrec.spring.config;

import fun.medrec.spring.domain.Ai.MyVectorStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

@Component
@Slf4j
public class Initializer {
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;
    @Value("${spring.data.redis.username:#{null}}")
    private String username;
    @Value("${spring.data.redis.password}")
    private String password;

    @Value("${spring.ai.openai.embedding.options.max-length}")
    private int maxLength;
    @Value("${spring.ai.openai.embedding.options.similarity}")
    private double similarity;
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.embedding.options.model}")
    private String model;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initAiUtil() {
        JedisPooled jedisPooled = new JedisPooled(host, port, username, password);
        MyVectorStore.init(stringRedisTemplate, jedisPooled, maxLength, similarity, baseUrl, apiKey, model);
    }
}
