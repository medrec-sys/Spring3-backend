package fun.medrec.spring.Ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.entity.Vector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Data
@AllArgsConstructor
@Slf4j
public class MyVectorStore {
    private EmbeddingModel embeddingModel;
    private RedisVectorStore redisVectorStore;
    private Vector vector;

    private static JedisPooled jedisPooled;
    private static String apiKey;
    private static String baseUrl;
    private static String embeddingName;
    private static StringRedisTemplate stringRedisTemplate;

    @Getter
    private static Cache<Integer, MyVectorStore> storeCache;

    public static void init(StringRedisTemplate stringRedisTemplate, JedisPooled jedisPooled, String baseUrl, String apiKey, String embeddingName) {
        MyVectorStore.stringRedisTemplate = stringRedisTemplate;
        MyVectorStore.jedisPooled = jedisPooled;
        MyVectorStore.apiKey = apiKey;
        MyVectorStore.baseUrl = baseUrl;
        MyVectorStore.embeddingName = embeddingName;

        MyVectorStore.storeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    public static MyVectorStore getStore(Integer id) {
        return storeCache.getIfPresent(id);
    }

    public static void deleteStore(Integer id) {
        storeCache.invalidate(id);
    }

    // 创建向量模型
    private EmbeddingModel createEmbeddingModel(Vector vector) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        MetadataMode metadataMode = MetadataMode.EMBED;

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingName)  // 指定模型
                .dimensions(vector.getDim())                  // 设置维度
                .build();
        // 2. 创建 EmbeddingModel
        return new OpenAiEmbeddingModel(openAiApi, metadataMode, options);
    }

    // 创建索引
    private void createIndex() {
        String indexName = vector.getIndexName();
        String prefix = vector.getPrefix();
        Integer dim = vector.getDim();

        try {
            stringRedisTemplate.execute((RedisConnection connection) -> {
                byte[][] args = new byte[][]{
                        indexName.getBytes(),
                        "ON".getBytes(),
                        "JSON".getBytes(),
                        "PREFIX".getBytes(),
                        "1".getBytes(),
                        prefix.getBytes(),
                        "SCHEMA".getBytes(),
                        "$.content".getBytes(),
                        "AS".getBytes(),
                        "content".getBytes(),
                        "TEXT".getBytes(),
                        "WEIGHT".getBytes(),
                        "1".getBytes(),
                        "$.embedding".getBytes(),
                        "AS".getBytes(),
                        "embedding".getBytes(),
                        "VECTOR".getBytes(),
                        "HNSW".getBytes(),
                        "10".getBytes(),
                        "TYPE".getBytes(),
                        "FLOAT32".getBytes(),
                        "DIM".getBytes(),
                        dim.toString().getBytes(),
                        "DISTANCE_METRIC".getBytes(),
                        "COSINE".getBytes(),
                        "M".getBytes(),
                        "16".getBytes(),
                        "EF_CONSTRUCTION".getBytes(),
                        "200".getBytes(),

                        // bookId
                        "$.bookId".getBytes(),
                        "AS".getBytes(),
                        "bookId".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // page
                        "$.page".getBytes(),
                        "AS".getBytes(),
                        "page".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // id
                        "$.id".getBytes(),
                        "AS".getBytes(),
                        "id".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // parentId
                        "$.parentId".getBytes(),
                        "AS".getBytes(),
                        "parentId".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // text
                        "$.text".getBytes(),
                        "AS".getBytes(),
                        "text".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                };   // 发送命令
                return connection.execute("FT.CREATE", args);
            });
        } catch (Exception ignored) {
        }


    }

    // 删除索引
    private void deleteIndex() {
        String indexName = vector.getIndexName();

        stringRedisTemplate.execute((RedisConnection connection) -> connection.execute("FT.DROPINDEX", indexName.getBytes()));

    }

    public MyVectorStore(Vector vector) {
        this.vector = vector;
        EmbeddingModel embeddingModel = createEmbeddingModel(vector);
        String indexName = vector.getIndexName();
        String prefix = vector.getPrefix();

        createIndex();

        this.redisVectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("bookId"),
                        RedisVectorStore.MetadataField.tag("page"),
                        RedisVectorStore.MetadataField.tag("id"),
                        RedisVectorStore.MetadataField.tag("text"),
                        RedisVectorStore.MetadataField.tag("parentId")
                )
                .initializeSchema(true)
                .build();
        this.embeddingModel = embeddingModel;
        log.info("加载向量库:{}", vector.getId());
        MyVectorStore.storeCache.put(vector.getId(), this);
    }

    // 添加文档到vectorStore
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        VectorStore vectorStore = this.redisVectorStore;
        int total = documents.size();
        int batchSize = 10;

        // 计算批次数量
        int batchCount = (total + batchSize - 1) / batchSize;

        // 根据CPU核心数和批次数量设置线程池大小
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), batchCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 使用线程安全的列表或直接处理
        List<Document> documentList = new CopyOnWriteArrayList<>(documents);

        for (int i = 0; i < batchCount; i++) {
            final int batchIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    int start = batchIndex * batchSize;
                    int end = Math.min(start + batchSize, total);
                    List<Document> batch = documentList.subList(start, end);

                    // 添加日志记录批次处理
                    synchronized (System.out) {
                        log.info("开始处理第 {}/{} 批次，文档数量：{}，范围：[{}-{}]",
                                batchIndex + 1, batchCount, batch.size(), start, end - 1);
                    }

                    // 存储批次文档
                    vectorStore.add(batch);

                    synchronized (System.out) {
                        log.info("\r完成处理第 {}/{} 批次，文档数量：{}",
                                batchIndex + 1, batchCount, batch.size());
                    }

                } catch (Exception e) {
                    log.error("处理第 {} 批次失败: {}", batchIndex + 1, e.getMessage(), e);
                    // 可以选择重试或者记录失败批次
                    throw new RuntimeException("批次处理失败", e);
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("所有批次处理完成，总文档数：{}，总批次数：{}", total, batchCount);
        } catch (Exception e) {
            log.error("批量添加文档过程中发生异常", e);
            throw new RuntimeException("批量添加文档失败", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("线程池未能在60秒内正常关闭，强制关闭");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("线程池关闭时被中断", e);
            }
        }
    }

    /**
     * @description 获取相似度
     */
    public List<Document> similaritySearchWithAiSummery(org.springframework.ai.vectorstore.SearchRequest request) {
        List<Document> documents = redisVectorStore.similaritySearch(request);

        return documents.stream().map(document -> {
            Map<String, Object> metadata = document.getMetadata();
            String text = (String) metadata.get("text");
            metadata.put("text", document.getText());
            String id = (String) metadata.get("id");
            return new Document(id, text, metadata);
        }).toList();
    }

    /**
     * @description 获取相似度
     */
    public List<Document> similaritySearchWithSplitSentence(org.springframework.ai.vectorstore.SearchRequest request) {
        // 查询子节点
        Filter.Expression childExpression = new FilterExpressionBuilder()
                .ne("parentId", "0")
                .build();
        SearchRequest childRequest = SearchRequest
                .from(request)
                .topK(request.getTopK())
                .filterExpression(childExpression)
                .build();
        List<Document> childDocuments = redisVectorStore.similaritySearch(childRequest);

        if (childDocuments.isEmpty()) {
            return List.of();
        }

        // 获取子查询的最高相似度
        Map<String, Double> childSimilarityScores = new HashMap<>();
        for (Document childDocument : childDocuments) {
            String parentId = (String) childDocument.getMetadata().get("parentId");
            Double similarityScore = childDocument.getScore();
            Double oldScore = childSimilarityScores.get(parentId);
            if (similarityScore == null) {
                continue;
            }
            if (oldScore == null || oldScore < similarityScore) {
                childSimilarityScores.put(parentId, similarityScore);
            }
        }
        log.debug("topK: {}", request.getTopK());
        log.debug("len: {}", childSimilarityScores.size());
        log.debug("子查询最高相似度: {}", JSON.toJSONString(childSimilarityScores));

        List<String> parentIds = childDocuments.stream()
                .map(document -> (String) document.getMetadata().get("parentId"))
                .distinct()  // 去重
                .toList();
        Filter.Expression parentExpression = new FilterExpressionBuilder()
                .in("id", parentIds.toArray())
                .build();
        SearchRequest parentRequest = SearchRequest
                .builder()
                .topK(request.getTopK())
                .similarityThreshold(0)
                .filterExpression(parentExpression)
                .build();

        List<Document> documents = redisVectorStore.similaritySearch(parentRequest);



        List<Document> list = documents.stream().map(document -> {
            String parentId = (String) document.getMetadata().get("id");
            Double score = childSimilarityScores.get(parentId);
            return document.mutate()
                    .score(score)
                    .build();
        }).toList();

        log.debug("list: {}", JSON.toJSONString(list, SerializerFeature.PrettyFormat));
        log.debug("childDocuments: {}", JSON.toJSONString(childDocuments, SerializerFeature.PrettyFormat));

        return list;

    }



    /**
     * @description 获取相似度
     */
    public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
        return similaritySearchWithSplitSentence(request);
    }

    // 清空知识库
    public void clear() {
        String prefix = vector.getPrefix();

        // 使用 scan 命令遍历所有匹配的键
        Set<String> keysToDelete = new HashSet<>();

        stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(100) // 每次扫描100个
                    .build();

            Cursor<byte[]> cursor = connection.scan(options);
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                keysToDelete.add(key);

                // 每积累1000个键删除一次，避免内存过大
                if (keysToDelete.size() >= 1000) {
                    stringRedisTemplate.delete(keysToDelete);
                    keysToDelete.clear();
                }
            }

            return null;
        });

        // 删除剩余的键
        if (!keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }

    // 删除文章
    public void delete(int id, int n) {
        int cycle = n / 10 + 1;
        Filter.Expression expression = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("bookId"),
                new Filter.Value(id + "")
        );
        for (int i = 0; i < cycle; i++) {
            redisVectorStore.delete(expression);
        }
    }

    // 释放资源
    public void release() {
        deleteIndex();
        clear();
    }
}
