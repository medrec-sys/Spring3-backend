package fun.medrec.spring.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import fun.medrec.spring.domain.bo.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public final class TextUtil {
    private TextUtil() {
        throw new AssertionError();

    }

    // 构建文本字符串
    private static String textSegmentToStr(TextSegment textSegment) {
        Map<String, String> filteredMetadata = textSegment.getMetadata()
                .entrySet()
                .stream()
                .filter(entry -> !"page".equalsIgnoreCase(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return JSON.toJSONString(filteredMetadata, SerializerFeature.PrettyFormat) +
                "\n" + textSegment.getContent();
    }

    // 总结文本
    public static List<TextSegment> summarizer(List<TextSegment> textSegments, Integer n) {
        List<String> stringWithMetaData = textSegments.stream()
                .map(TextUtil::textSegmentToStr)
                .toList();
        int length = textSegments.size();
        List<TextSegment> result = new CopyOnWriteArrayList<>(textSegments);
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), length);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < length; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String text = stringWithMetaData.get(index);
                    StringBuilder contextBeforeBuilder = new StringBuilder();
                    StringBuilder contextAfterBuilder = new StringBuilder();

                    // 构建上文
                    for (int j = index - n; j < index; j++) {
                        if (j >= 0) {
                            contextBeforeBuilder.append("上文").append(n - (index - j) + 1).append(":");
                            contextBeforeBuilder.append(stringWithMetaData.get(j));
                        }
                    }

                    // 构建下文
                    for (int j = index + 1; j < index + n + 1; j++) {
                        if (j < length) {
                            contextAfterBuilder.append("下文").append(j - index).append(":");
                            contextAfterBuilder.append(stringWithMetaData.get(j));
                        }
                    }

                    if (contextAfterBuilder.isEmpty()) {
                        contextAfterBuilder.append("无");
                    }
                    if (contextBeforeBuilder.isEmpty()) {
                        contextBeforeBuilder.append("无");
                    }

                    String contextBefore = contextBeforeBuilder.toString();
                    String contextAfter = contextAfterBuilder.toString();

                    // 调用AI总结（假设ModelUtil.summarizer是线程安全的）
                    String summarizer = ModelUtil.summarizer(text, contextBefore, contextAfter);

                    // 日志记录（使用同步块或日志框架本身是线程安全的）
                    synchronized (System.out) {
                        log.info("ai总结段落：{}-{}", length, index);
                    }

                    // 更新结果（CopyOnWriteArrayList保证线程安全）
                    result.get(index).setSummary(summarizer);

                } catch (Exception e) {
                    log.error("处理第 {} 个片段失败: {}", index, e.getMessage(), e);
                    // 可以设置默认值或标记失败
                    result.get(index).setSummary("总结失败: " + e.getMessage());
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        return result;
    }

    // 文本片段转Document
    private static Document TextToDocument(TextSegment textSegment, Integer bookId) {
        String summary = textSegment.getSummary();
        Integer index = textSegment.getId();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", bookId + "_" + index);
        metadata.put("bookId", bookId + "");
        if (textSegment.getMetadata() != null) {
            String page = textSegment.getMetadata().get("page");
            String content = textSegment.getContent();
            metadata.put("page", page);
            metadata.put("text", content);
        }
        String docId = bookId + ":" + index;
        return new Document(docId, summary, metadata);
    }

    // 文本树转Document
    public static List<Document> TextToDocuments(List<TextSegment> textSegments, Integer bookId) {
        List<Document> documents = new ArrayList<>();


        for (TextSegment textSegment : textSegments) {
            Document document = TextToDocument(textSegment, bookId);
            documents.add(document);
        }

        return documents;
    }

}

