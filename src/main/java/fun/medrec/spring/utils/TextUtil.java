package fun.medrec.spring.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.exception.BusinessException;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.springframework.ai.document.Document;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public final class TextUtil {
    @Data
    public static class TextData {
        private int id = -1;
        private final String fileName;
        private final List<String> texts;
        private final List<Integer> indexes;
    }

    private static final int MAX_LENGTH = 2000;
    // 语言识别
    private static final LanguageDetector languageDetector;
    // 英文断句
    private static final SentenceDetectorME sentenceDetector;

    static {
        try {
            languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(new LanguageProfileReader().readAllBuiltIn())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream modelIn = TextUtil.class.getResourceAsStream("/model/en-sent.bin")) {
            if (modelIn == null) {
                throw new BusinessException("modelIn为null");
            }
            SentenceModel model = new SentenceModel(modelIn);
            sentenceDetector = new SentenceDetectorME(model);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class PdfHandler extends DefaultHandler {
        StringBuilder currentPageText = new StringBuilder();
        int currentPage = 0;

        int divCount = 0;

        @Getter
        final List<String> pageContents = new ArrayList<>();


        @Override
        public void startElement(String uri, String localName, String qName,
                                 org.xml.sax.Attributes attributes) {
            // 检测到页面开始的标记
            if ("div".equals(localName)) {
                if ("page".equals(attributes.getValue("class"))) {
                    currentPage++;
                }
                divCount++;

            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            currentPageText.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (("div".equals(localName))) {
                divCount--;
                if (divCount == 0) {
                    pageContents.add(currentPageText.toString());
                    currentPageText = new StringBuilder();
                }

            }
        }
    }

    private TextUtil() {
        throw new AssertionError();

    }

    public static String getLanguage(List<String> texts) {
        StringBuilder sb = new StringBuilder();
        for (String content : texts) {
            if (content != null && !content.isEmpty()) {
                sb.append(content);
                // 添加空格分隔，避免单词连在一起影响检测
                sb.append(' ');
            }
        }


        TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        TextObject textObject = textObjectFactory.forText(sb);
        List<DetectedLanguage> probabilities = languageDetector.getProbabilities(textObject);
        LdLocale locale = probabilities.getFirst().getLocale();
        double similarity = probabilities.getFirst().getProbability();
        for (DetectedLanguage probability : probabilities) {
            if (probability.getProbability() > similarity) {
                locale = probability.getLocale();
                similarity = probability.getProbability();
            }
        }
        return locale.getLanguage();
    }

    //分割句子
    public static String[] splitSentence(String text, String language) {
        if ("zh".equals(language)) {
            return text.replaceAll("\\s", "").split("[。！？]");
        } else if ("en".equals(language)) {
            return sentenceDetector.sentDetect(text);
        } else {
            return text.split("[.!?]");
        }
    }

    public static TextData readPdf(InputStream inputStream, String fileName) {
        try {
            // 自定义 ContentHandler
            PdfHandler handler = new PdfHandler();

            // 设置解析上下文
            ParseContext context = new ParseContext();
            PDFParser pdfParser = new PDFParser();

            // 执行解析
            Metadata metadata = new Metadata();
            pdfParser.parse(inputStream, handler, metadata, context);


            List<String> pageContents = handler.getPageContents();

            // 判断语言
            String language = getLanguage(pageContents);
            log.info("文档语言: {}", language);

            // 出空格和分割
            List<String> pageContentsAfterDeal = new ArrayList<>();
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < pageContents.size(); i++) {
                String[] sentence = splitSentence(pageContents.get(i), language);
                for (String sentence1 : sentence) {
                    if (sentence1.length() < MAX_LENGTH) {
                        pageContentsAfterDeal.add(sentence1);
                        indexes.add(i + 1);
                    } else {
                        int chunkSize = MAX_LENGTH;
                        int length = sentence1.length();

                        for (int j = 0; j < length; j += chunkSize) {
                            int end = Math.min(j + chunkSize, length);
                            String chunk = sentence1.substring(j, end);
                            pageContentsAfterDeal.add(chunk);
                            indexes.add(i + 1);
                        }
                    }
                }
            }

            //  合并段落末尾被分割的句子
            int i = 0;
            while (i < pageContentsAfterDeal.size() - 1) {
                if (!Objects.equals(indexes.get(i), indexes.get(i + 1))) {
                    pageContentsAfterDeal.set(i, (pageContentsAfterDeal.get(i) + pageContentsAfterDeal.get(i + 1)));
                    pageContentsAfterDeal.remove(i + 1);
                    indexes.remove(i + 1);
                }
                i++;
            }

            return new TextData(fileName, pageContentsAfterDeal, indexes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static TextData readPdf(String pdfPath) {
        try (FileInputStream inputStream = new FileInputStream(pdfPath)) {
            String fileName = new File(pdfPath).getName();
            return readPdf(inputStream, fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Document> TextToDocument(TextData textData) {
        List<String> texts = textData.getTexts();
        List<Integer> indexes = textData.getIndexes();
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", textData.getId() + "");
            metadata.put("page", indexes.get(i) + "");
            Document document = new Document(texts.get(i), metadata);
            documents.add(document);

        }
        return documents;
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

        // 使用线程安全的列表存储结果
        List<TextSegment> result = new CopyOnWriteArrayList<>(textSegments);

        // 创建线程池，建议根据CPU核心数设置
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


    public static List<TextSegment> batchAggregateSummaries(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return textSegments;
        }

        int length = textSegments.size();
        // 使用线程安全的列表存储结果
        List<TextSegment> result = new CopyOnWriteArrayList<>(textSegments);

        // 根据CPU核心数和任务数量设置线程池大小
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


    private static Document TextToDocument(TextSegment textSegment, Integer bookId) {
        String summary = textSegment.getSummary();
        Integer index = textSegment.getIndex();
        List<Integer> childrenIds = textSegment.getChildren().stream()
                .map(TextSegment::getIndex)
                .toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("index", index + "");
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
        return new Document(summary, metadata);
    }

    public static List<Document> TextToDocuments(TextSegment textTree, Integer bookId) {
        List<Document> documents = new ArrayList<>();
        List<TextSegment> queue = new ArrayList<>();
        queue.add(textTree);

        while (!queue.isEmpty()) {
            TextSegment textSegment = queue.removeFirst();
            documents.add(TextToDocument(textSegment, bookId));
            queue.addAll(textSegment.getChildren());
        }

        return documents;
    }

}

