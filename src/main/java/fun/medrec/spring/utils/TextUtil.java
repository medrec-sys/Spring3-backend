package fun.medrec.spring.utils;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.springframework.ai.document.Document;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.*;

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

    public static TextData readPdf(String pdfPath) {


        try {
            // 自定义 ContentHandler
            PdfHandler handler = new PdfHandler();

            // 设置解析上下文
            ParseContext context = new ParseContext();
            PDFParser pdfParser = new PDFParser();

            // 执行解析
            try (FileInputStream inputStream = new FileInputStream(pdfPath)) {
                Metadata metadata = new Metadata();
                pdfParser.parse(inputStream, handler, metadata, context);
            }


            List<String> pageContents = handler.getPageContents();


            // 出空格和分割
            List<String> pageContentsAfterDeal = new ArrayList<>();
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < pageContents.size(); i++) {
                String[] sentence = pageContents.get(i).replaceAll("\\s", "").split("[。]");
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

            return new TextData(new File(pdfPath).getName(), pageContentsAfterDeal, indexes);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    public static List<Document> TextToDocument(TextData textData) {
        List<String> texts = textData.getTexts();
        List<Integer> indexes = textData.getIndexes();
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", textData.getId());
            metadata.put("page", indexes.get(i));
            Document document = new Document(texts.get(i), metadata);
            documents.add(document);

        }
        return documents;
    }


}
