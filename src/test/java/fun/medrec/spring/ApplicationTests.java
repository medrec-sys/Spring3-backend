package fun.medrec.spring;


import com.fasterxml.jackson.databind.SerializationFeature;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@SpringBootTest
class ApplicationTests {

    String path ="D:/Source/windows/Desktop/hypertension_split.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test01() throws Exception  {
        List<TextSegment> textSegments = objectMapper.readValue(
                new File(path),
                new TypeReference<>() {
                }
        );

        textSegments = TextUtil.summarizer(textSegments, 1);

        // 保存到JSON文件
        String outputPath = "D:/Source/windows/Desktop/summarized_segments_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(new File(outputPath), textSegments);

        log.info("处理完成，共 {} 个片段，已保存至: {}", textSegments.size(), outputPath);

    }

}