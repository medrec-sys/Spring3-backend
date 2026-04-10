package fun.medrec.spring.service.impl;

import fun.medrec.spring.config.ArgsConfig;
import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.service.HttpService;
import fun.medrec.spring.utils.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class HttpServiceImpl implements HttpService {
    private static final WebClient webClient = WebClient.create();
    private static final Duration TIMEOUT = Duration.ofSeconds(30000);

    final
    ArgsConfig argsConfig;

    public HttpServiceImpl(ArgsConfig argsConfig) {
        this.argsConfig = argsConfig;
    }

    @Override
    public Result<List<TextSegment>> fileToMd(FileData fileData) {
        try {
            ByteArrayResource resource = new ByteArrayResource(fileData.getBytes()) {
                @Override
                public String getFilename() {
                    return fileData.getName();
                }
            };

            // 构建 multipart/form-data 请求体
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", resource)
                    .filename(fileData.getName() == null ? "file" : fileData.getName() )
                    .contentType(MediaType.valueOf(fileData.getContentType() == null ? "application/octet-stream" : fileData.getContentType()));

            String response = webClient.post()
                    .uri(argsConfig.fastApiUrl + "/api/file")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            return HttpUtil.parseResponseBody(response, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("发送文件内容失败: {}", argsConfig.fastApiUrl, e);
            return Result.error(e.getMessage());
        }
    }
}
