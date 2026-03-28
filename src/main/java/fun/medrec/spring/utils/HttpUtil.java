package fun.medrec.spring.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.medrec.spring.domain.common.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpUtil {
    // 解析响应体
    public static <T> Result<T> parseResponseBody(String response) {
        try {
            if (response == null) {
                return Result.error("响应体为空");
            }

            // 解析外层 Result
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            int code = root.get("code").asInt();
            String message = root.get("message").asText();

            JsonNode dataNode = root.get("data");
            T data = mapper.readValue(
                    dataNode.toString(),
                    new TypeReference<>() {
                    }
            );

            return new Result<>(code, message, data);
        } catch (Exception e) {
            log.error("解析响应体失败", e);
            return Result.error("解析响应体失败");
        }
    }
}
