package fun.medrec.spring.utils;

import fun.medrec.spring.domain.bo.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author 彭超
 * @version 1.1
 * @description 文本处理工具类, 将原始文本处理成统一格式TextSegment，在进行文本增强处理，形成可以直接存储的文本块
 * @date 2026-04-24 16:05
 */
@Slf4j
@Component
public final class TextUtil {
    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.CHINESE);


    /**
     * @description 将MinerU的文本Json转换为TextSegment
     */
    public List<TextSegment> textSegmentsFromMiner(List<MinerUtil.ContentItem> contents, Integer bookId) {
        // 转换为TextSegment
        int id = 1;
        List<TextSegment> list = new ArrayList<>();
        for (MinerUtil.ContentItem content : contents) {
            TextSegment textSegment = new TextSegment();
            textSegment.setId(id);
            textSegment.setContent(content.getText());
            textSegment.setPage(content.getPageIdx());
            textSegment.setBookId(bookId);
            textSegment.setMap("parentId", "0");
            id++;
            list.add(textSegment);
        }
        log.debug("文本长度: {} ", list.size());

        return list;
    }

    /**
     * @description 文本增强：将段落分割成小句子
     */
    public List<TextSegment> strengthenWithSpilt(List<TextSegment> textSegments) {
        int id = textSegments.size();
        int len = textSegments.size();
        log.info("文本增强：将段落分割成小句子size:{}", id);
        // 分割为句子
        for (int i = 0; i < len; i++) {
            TextSegment textSegment = textSegments.get(i);
            String text = textSegment.getContent();
            iterator.setText(text);

            int start = iterator.first();
            int end = iterator.next();

            while (end != BreakIterator.DONE) {
                String sentence = text.substring(start, end);
                if (sentence.length() <= 10) {
                    start = end;
                    end = iterator.next();
                    continue;
                }

                TextSegment newTextSegment = new TextSegment();
                newTextSegment.setContent(sentence);
                newTextSegment.setMap("parentId", textSegment.getBookId() + "_" +textSegment.getId() );
                newTextSegment.setPage(textSegment.getPage());
                newTextSegment.setBookId(textSegment.getBookId());
                newTextSegment.setId(id);
                id++;

                textSegments.add(newTextSegment);

                start = end;
                end = iterator.next();
            }
        }

        log.info("增强后长度：{}", textSegments.size());

        return textSegments;
    }


    /**
     * @description 将TextSegment转换成Document，直接可以存储到redis
     */
    public List<Document> toDocsWithSplit(List<TextSegment> textSegments) {

        Integer bookId = textSegments.getFirst().getBookId();

        List<Document> documents = new ArrayList<>();

        for (TextSegment textSegment : textSegments) {
            String index = bookId + "_" + textSegment.getId();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", index);
            metadata.put("bookId", bookId + "");
            metadata.put("parentId", textSegment.getMap("parentId"));
            metadata.put("page", textSegment.getPage() + "");
            String docId = bookId + ":" + index;
            documents.add(new Document(docId, textSegment.getContent(), metadata));
        }
        return documents;
    }




    /**
     * @description Ai总结文本
     */
    public List<TextSegment> strengthenWithSummary(List<TextSegment> textSegments, Integer n) {
        List<String> stringWithMetaData = textSegments.stream()
                .map(TextSegment::getContent)
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
                    result.get(index).setMap( "summary", summarizer);

                } catch (Exception e) {
                    log.error("处理第 {} 个片段失败: {}", index, e.getMessage(), e);
                    // 可以设置默认值或标记失败
                    result.get(index).setMap("summary", "总结失败: " + e.getMessage());
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



    /**
     * @description 将TextSegment转换成Document，直接可以存储到redis
     */

    public List<Document> toDocsWithSummary(List<TextSegment> textSegments) {
        List<Document> documents = new ArrayList<>();

        for (TextSegment textSegment : textSegments) {
            Integer bookId = textSegment.getBookId();
            String summary = textSegment.getMap("summary");
            int index = textSegment.getId();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", bookId + "_" + index);
            metadata.put("bookId", bookId + "");
            if (textSegment.getMetadata() != null) {
                String page = String.valueOf(textSegment.getPage());
                String content = textSegment.getContent();
                metadata.put("page", page);
                metadata.put("text", content);
            }
            String docId = bookId + ":" + index;
            documents.add(new Document(docId, summary, metadata));
        }

        return documents;
    }

}

