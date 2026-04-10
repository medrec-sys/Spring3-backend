package fun.medrec.spring.domain.bo;

import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileData {
    private String contentType;
    private byte[] bytes;
    private String name;

    @SneakyThrows
    public FileData(MultipartFile multipartFile) {
        this.contentType = multipartFile.getContentType();
        this.bytes = multipartFile.getBytes();
        this.name = multipartFile.getOriginalFilename();
    }
}
