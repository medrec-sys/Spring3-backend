package fun.medrec.spring.service;

import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.Result;

import java.util.List;

public interface HttpService {
    Result<List<TextSegment>> fileToMd(FileData fileData);

}
