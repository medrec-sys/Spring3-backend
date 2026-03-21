package fun.medrec.spring;



import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;


@Slf4j
@SpringBootTest
class ApplicationTests {

    String path = "D:/Source/大学/南昌大学英.pdf";


    FileSystemResource resource = new FileSystemResource(path);
    @Test
    void test01() throws Exception  {
        TextUtil.TextData textData = TextUtil.readPdf(path);
        for (int i = 0; i < textData.getTexts().size(); i++) {
            log.info("{} {}", textData.getIndexes().get(i), textData.getTexts().get(i));
        }


    }

}