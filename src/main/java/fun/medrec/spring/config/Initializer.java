package fun.medrec.spring.config;

import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyVectorStore;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import fun.medrec.spring.utils.MinioUtil;

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

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.embedding.options.model}")
    private String model;

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    @Value("${minio.clientPoint}")
    private String clientPoint;
    @Value("${minio.serverPoint}")
    private String serverPoint;
    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;
    @Value("${minio.bucket}")
    private String bucket;

    final
    StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public Initializer(StringRedisTemplate stringRedisTemplate, JdbcTemplate jdbcTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initAiUtil() {
        JedisPooled jedisPooled = new JedisPooled(host, port, username, password);
        MyVectorStore.init(stringRedisTemplate, jedisPooled, baseUrl, apiKey, model);

        AiAgent.init(jdbcTemplate, baseUrl, apiKey, chatModel);

        MinioClient minioClient = MinioClient.builder()
                .endpoint(serverPoint)
                .credentials(accessKey, secretKey)
                .build();
        MinioUtil.init(minioClient, bucket, clientPoint);
    }
}
