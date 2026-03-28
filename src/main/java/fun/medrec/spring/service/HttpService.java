package fun.medrec.spring.service;

import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HttpService {
    Result<List<TextSegment>> fileToMd(MultipartFile multipartFile);

}
