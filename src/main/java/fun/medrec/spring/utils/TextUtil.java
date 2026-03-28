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

    // 批量聚合总结
    public static List<TextSegment> batchAggregateSummaries(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return textSegments;
        }

        int length = textSegments.size();
        List<TextSegment> result = new CopyOnWriteArrayList<>(textSegments);
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), length);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < length; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    TextSegment textSegment = result.get(index);
                    List<String> childrenSummary = textSegment.getChildren().stream()
                            .map(TextSegment::getSummary)
                            .toList();

                    // 如果没有子节点，跳过聚合
                    if (childrenSummary.isEmpty()) {
                        synchronized (System.out) {
                            log.info("跳过第 {}/{} 个节点，无子节点", index + 1, length);
                        }
                        return;
                    }

                    // 调用AI聚合摘要
                    String summary = ModelUtil.aggregateSummaries(childrenSummary);
                    textSegment.setSummary(summary);

                    synchronized (System.out) {
                        log.info("聚合完成：第 {}/{} 个父节点，子节点数量：{}",
                                index + 1, length, childrenSummary.size());
                    }

                } catch (Exception e) {
                    log.error("聚合第 {} 个节点失败: {}", index, e.getMessage(), e);
                    // 可以设置默认值或保留原摘要
                    TextSegment textSegment = result.get(index);
                    if (textSegment.getSummary() == null || textSegment.getSummary().isEmpty()) {
                        textSegment.setSummary("聚合失败: " + e.getMessage());
                    }
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
                log.error("线程池中断异常", e);
            }
        }

        return result;
    }

    // 文本片段转Document
    private static Document TextToDocument(TextSegment textSegment, Integer bookId) {
        String summary = textSegment.getSummary();
        Integer index = textSegment.getId();
        List<String> childrenIds = textSegment.getChildren().stream()
                .map((child) -> (bookId + "_" + child.getId()))
                .toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", bookId + "_" + index);
        metadata.put("bookId", bookId + "");
        if (textSegment.getMetadata() != null) {
            String page = textSegment.getMetadata().get("page");
            String content = textSegment.getContent();
            metadata.put("page", page);
            metadata.put("text", content);
        }
        if (!childrenIds.isEmpty()) {
            metadata.put("childrenIds", childrenIds);
        }
        String docId = bookId + ":" + index;
        return new Document(docId, summary, metadata);
    }

    // 文本树转Document
    public static List<Document> TextToDocuments(TextSegment textTree, Integer bookId) {
        List<Document> documents = new ArrayList<>();
        List<TextSegment> queue = new ArrayList<>();
        queue.add(textTree);

        while (!queue.isEmpty()) {
            TextSegment textSegment = queue.removeFirst();
            documents.add(TextToDocument(textSegment, bookId));
            queue.addAll(textSegment.getChildren());
        }

        Document first = documents.getFirst();
        Map<String, Object> metadata = first.getMetadata();
        metadata.put("isRoot", "1");
        documents.set(0,
                first.mutate()
                        .metadata(metadata)
                        .build());

        return documents;
    }

}

