package fun.medrec.spring.utils;

import fun.medrec.spring.domain.bo.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.*;

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
            textSegment.setBbox(content.getBbox());
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
        int id = textSegments.size() + 1;
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
                newTextSegment.setBbox(textSegment.getBbox());
                newTextSegment.setMap("parentId", textSegment.getBookId() + "_" + textSegment.getId());
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
            metadata.put("bbox", textSegment.getBbox().toString());
            String docId = bookId + ":" + index;
            documents.add(new Document(docId, textSegment.getContent(), metadata));
        }
        return documents;
    }

}

