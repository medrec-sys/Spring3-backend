package fun.medrec.spring;


import fun.medrec.spring.utils.EmbeddingUtil;
import fun.medrec.spring.utils.PdfUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class ApplicationTests {


    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void testExtractPdfText() {
        String pdfPath = "D:\\Source\\windows\\Downloads\\高血压.pdf";

        PdfUtil.PdfData pdfData = PdfUtil.readPdf(pdfPath);

        pdfData = EmbeddingUtil.mergeSentence(pdfData, embeddingModel);


    }


}