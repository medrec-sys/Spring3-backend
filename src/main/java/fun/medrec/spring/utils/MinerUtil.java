package fun.medrec.spring.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.j256.simplemagic.ContentType;
import fun.medrec.spring.exception.BusinessException;
import io.micrometer.core.instrument.util.IOUtils;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author 彭超
 * @version 1.0
 * @description 使用MinerU将文件转为md
 * @date 2026-04-23 21:08
 */
@Component
@Slf4j
public class MinerUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final RestClient restClient = RestClient.create();

    @Value("${minerU.token}")
    private String token;
    // minerU服务地址，用于上传本地文件
    String url = "https://mineru.net/api/v4/file-urls/batch";
    String batchUrl = "https://mineru.net/api/v4/extract-results/batch";

    // 支持的文件格式
    private final List<String> contentTypes = List.of(
            ContentType.PDF.getMimeType(),  // pdf
            ContentType.MICROSOFT_WORD.getMimeType(), //  doc
            ContentType.MICROSOFT_POWERPOINT_XML.getMimeType(), // ppt
            ContentType.MICROSOFT_POWERPOINT.getMimeType() // pptx
    );
    // 最大文件大小  200M
    private final Integer maxSize = 200 * 1024 * 1024;
    // 用于合并文件的块大小
    private final Integer blockMinSize = 100;
    // 用于分割的文本块的最大值
    private final Integer blockMaxSize = 2000;


    /**
     * @description 校验文件
     */
    private void validateFiles(List<MultipartFile> files) {
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException("文件不能为空");
            }

            if (!contentTypes.contains(file.getContentType())) {
                throw new BusinessException("不支持的文件格式:" + file.getContentType());
            }

            if (file.getSize() > maxSize) {
                throw new BusinessException("文件大小超过" + maxSize / 1024 / 1024 + "M");
            }
        }
    }

    /**
     * @return 返回云存储url，通过这个url解析文件
     * @description 将文件通过minerU上传至云服务
     */
    private MinerUResponse.MinerUData applyParseUrl(List<MultipartFile> files) {
        // 1. 构建请求数据
        List<Map<String, Object>> fileList = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", file.getOriginalFilename());
            fileInfo.put("data_id", "abcd");
            fileInfo.put("is_ocr", true);
            fileList.add(fileInfo);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("files", fileList);
        requestBody.put("model_version", "vlm");

        try {
            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(JSON.toJSONString(requestBody))
                    .retrieve()
                    .body(Map.class);
            MinerUResponse minerUResponse = objectMapper.readValue(JSON.toJSONString(response), MinerUResponse.class);
            log.debug("上传文件成功: {}", JSON.toJSONString(minerUResponse, SerializerFeature.PrettyFormat));
            return minerUResponse.getData();
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new BusinessException("上传文件失败");
        }
    }

    /**
     * @param files      文件列表
     * @param minerUData 包含文件id和网络地址
     * @description 开始解析
     */
    private void beginParse(List<MultipartFile> files, MinerUResponse.MinerUData minerUData) {
        List<String> fileUrls = minerUData.getFileUrls();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String uploadUrl = fileUrls.get(i);

            try {
                log.debug("开始上传文件: {}", uploadUrl);

                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL(uploadUrl).openConnection();
                conn.setRequestMethod("PUT");
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(file.getBytes());
                }

                if (conn.getResponseCode() / 100 != 2) {
                    throw new BusinessException("上传失败: " + file.getOriginalFilename());
                }
                conn.disconnect();
                log.debug("文件上传成功: {}", file.getOriginalFilename());
            } catch (Exception e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
                throw new BusinessException("文件上传失败: " + file.getOriginalFilename());
            }
        }
    }

    /**
     * @description 读取解析结果中的content
     */
    public List<ContentItem> getContent(String zipUrl) {
        byte[] zipData;
        try {
            // 下载 ZIP
            zipData = restClient.get()
                    .uri(zipUrl)
                    .retrieve()
                    .body(byte[].class);
            log.debug("下载 ZIP 成功");
        } catch (Exception e) {
            log.error("下载或解析 ZIP 失败", e);
            throw new BusinessException("处理失败: " + e.getMessage());
        }

        if (zipData != null) {
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipData))) {
                ZipArchiveEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.endsWith("_content_list.json")) {
                        String jsonContent = IOUtils.toString(zis, StandardCharsets.UTF_8);
                        // 解析 JSON
                        return JSON.parseArray(jsonContent, ContentItem.class);
                    }
                }
            } catch (Exception e) {
                log.error("处理失败: {}", e.getMessage(), e);
                throw new BusinessException("处理失败: " + e.getMessage());
            }
        }
        throw new BusinessException("ZIP 中没有找到 content_list.json 文件");
    }

    /**
     * @description 处理数据
     */
    public List<ContentItem> handleContent(List<ContentItem> content) {
        List<ContentItem> result = new ArrayList<>();
        for (ContentItem item : content) {
            // 过滤PAGE_NUMBER， HEADER
            if (
                    item.getType() == ContentItem.MinerUContentType.PAGE_NUMBER ||
                            item.getType() == ContentItem.MinerUContentType.HEADER
            ) {
                continue;
            }

            // 处理图片--现在暂时直接过滤
            if (item.getType() == ContentItem.MinerUContentType.IMAGE) {
                continue;
            }

            // 处理文本,公式
            if (item.getType() == ContentItem.MinerUContentType.TEXT ||
                    item.getType() == ContentItem.MinerUContentType.EQUATION) {
                if (item.getText().length() <= blockMaxSize) {
                    result.add(item);
                } else {
                    int len = item.getText().length();
                    int blockNum = (len + blockMaxSize - 1) / blockMaxSize;
                    for (int i = 0; i < blockNum; i++) {
                        int start = i * blockMaxSize;
                        int end = Math.min(start + blockMaxSize, len);
                        String chunk = item.getText().substring(start, end);
                        ContentItem chunkItem = ContentItem.copyToText(item);
                        chunkItem.setText(chunk);
                        result.add(chunkItem);
                    }
                }
                continue;
            }

            // 处理表格
            if (item.getType() == ContentItem.MinerUContentType.TABLE) {
                if (!StringUtils.hasText(item.getTableBody())) {
                    continue;
                }

                List<String> strings = new ArrayList<>();
                if (item.getTableBody().length() < blockMaxSize) {
                    strings.add(item.getTableBody());
                } else {
                    int len = item.getTableBody().length();
                    int blockNum = (len + blockMaxSize - 1) / blockMaxSize;
                    for (int i = 0; i < blockNum; i++) {
                        int start = i * blockMaxSize;
                        int end = Math.min(start + blockMaxSize, len);
                        String chunk = item.getTableBody().substring(start, end);
                        strings.add(chunk);
                    }
                }

                for (String string : strings) {
                    ContentItem contentItem = ContentItem.copyToText(item);
                    String title = String.join(", ", item.getTableCaption());
                    String footnote = String.join(", ", item.getTableFootnote());
                    StringBuilder text = new StringBuilder();
                    if (StringUtils.hasText(title)) {
                        text.append("表格标题:").append(title).append("\n");
                    }
                    text.append("表格内容:").append(string).append("\n");
                    if (StringUtils.hasText(footnote)) {
                        text.append("表格脚注:").append(footnote).append("\n");
                    }
                    contentItem.setText(text.toString());
                    result.add(contentItem);

                }
                continue;
            }

            // 处理列表
            if (item.getType() == ContentItem.MinerUContentType.LIST) {
                String join = String.join("", item.getListItems());
                if (!StringUtils.hasText(join)) {
                    continue;
                }
                StringBuilder tempStr = new StringBuilder(item.getListItems().getFirst());
                for (int i = 1; i < item.getListItems().size(); i++) {
                    String s = item.getListItems().get(i);
                    if (tempStr.length() + s.length() < blockMaxSize) {
                        tempStr.append(s);
                    } else {
                        ContentItem newItem = ContentItem.copyToText(item);
                        newItem.setText(tempStr.toString());
                        result.add(newItem);
                        tempStr = new StringBuilder(item.getListItems().get( i));
                    }
                }

                if (!tempStr.isEmpty()) {
                    ContentItem newItem = ContentItem.copyToText(item);
                    newItem.setText(tempStr.toString());
                    result.add(newItem);
                }
            }

        }

        // 合并字数过少的文本
        List<ContentItem> merged = new ArrayList<>();
        for (ContentItem curr : result) {
            if (merged.isEmpty() || merged.getLast().getText().length() >= blockMinSize) {
                merged.add(curr);
            } else {
                ContentItem last = merged.getLast();
                last.setText(last.getText() + curr.getText());
            }
        }
        return merged;
    }


    /**
     * @param zipUrl zip地址
     * @description 处理解析结果
     */
    public List<ContentItem> handleParseResult(String zipUrl) {
        List<ContentItem> content = getContent(zipUrl);
        return handleContent(content);
    }

    /**
     * @return batchId 通过batchId可以获取解析结果
     * @description 上传文件并解析
     */

    public String uploadAndParse(List<MultipartFile> files) {
        validateFiles(files);
        MinerUResponse.MinerUData minerUData = applyParseUrl(files);
        beginParse(files, minerUData);
        return minerUData.getBatchId();
    }

    /**
     * @description 获取解析结果zip地址
     */
    public PollingResult getZipUrl(String batchId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(batchUrl + "/" + batchId)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);

            MinerUExtractResultResponse minerUResponse = objectMapper.readValue(JSON.toJSONString(response), MinerUExtractResultResponse.class);

            // 解析结果
            PollingResult pollingResult = new PollingResult();
            String info = "正在转换md\n" +
                    String.join("\n", minerUResponse.getData().getExtractResult().stream().map(item ->
                    {
                        StringBuilder msg = new StringBuilder().append(item.getFileName()).append(": ").append(item.getState().getMsg());
                        if (item.getExtractProgress() != null) {
                            msg.append("---").append(item.getExtractProgress().getTotalPages()).append("/").append(item.getExtractProgress().getExtractedPages());
                        }

                        return msg;
                    }).toList());

            pollingResult.setInfo(info);
            List<MinerUExtractResultResponse.MinerUTaskState> stateList = minerUResponse.getData().extractResult.stream().map(MinerUExtractResultResponse.ExtractResultItem::getState).toList();
            log.debug("解析状态：{}", info);
            for (MinerUExtractResultResponse.MinerUTaskState state : stateList) {
                if (state == MinerUExtractResultResponse.MinerUTaskState.FAILED) {
                    log.error("解析失败: {}", minerUResponse);
                    throw new BusinessException("解析失败: " + minerUResponse);
                }
                if (state != MinerUExtractResultResponse.MinerUTaskState.DONE) {
                    pollingResult.setSuccess(false);
                    return pollingResult;
                }
            }
            log.debug("获取解析结果成功: {}", JSON.toJSONString(minerUResponse, SerializerFeature.PrettyFormat));
            pollingResult.setSuccess(true);
            pollingResult.setZipUrls(minerUResponse.data.extractResult.stream().map(MinerUExtractResultResponse.ExtractResultItem::getFullZipUrl).toList());
            return pollingResult;
        } catch (Exception e) {
            log.error("获取解析结果失败", e);
            throw new BusinessException("获取解析结果失败: " + e.getMessage());
        }
    }

    /**
     * 申请云存储返回结果数据类
     */
    @Data
    public static class MinerUResponse {
        private Integer code;
        private String msg;
        private String traceId;
        private MinerUData data;

        @Data
        static
        class MinerUData {
            private String batchId;
            private List<String> fileUrls;
        }
    }

    /**
     * 解析结果响应数据类
     */
    @Data
    public static class MinerUExtractResultResponse {
        private Integer code;
        private String msg;
        private String traceId;  // 对应 JSON 中的 trace_id
        private ExtractResultData data;

        @Data
        public static class ExtractResultData {
            private String batchId;  // 对应 JSON 中的 batch_id
            private List<ExtractResultItem> extractResult;  // 对应 JSON 中的 extract_result
        }

        @Data
        public static class ExtractResultItem {
            private String dataId;  // 对应 JSON 中的 data_id
            private String fileName;  // 对应 JSON 中的 file_name
            private MinerUTaskState state;  // 状态: done, processing, failed
            private String errMsg;  // 对应 JSON 中的 err_msg
            private String fullZipUrl;  // 对应 JSON 中的 full_zip_url
            private ExtractProgress extractProgress;  // 解析进度
        }

        /**
         * 解析进度数据类
         */
        @Data
        public static class ExtractProgress {
            private Integer extractedPages;
            private Integer totalPages;
            private String startTime;
        }

        /**
         * MinerU 任务状态枚举
         */
        @Getter
        public enum MinerUTaskState {

            WAITING_FILE("waiting-file", "等待文件上传"),  // 等待文件上传
            PENDING("pending", "排队中"),            // 排队中
            RUNNING("running", "正在解析"),            // 正在解析
            CONVERTING("converting", "格式转换中"),      // 格式转换中
            DONE("done", "完成"),                  // 完成
            FAILED("failed", "失败");              // 失败

            @JsonValue
            private final String code;
            private final String msg;

            MinerUTaskState(String code, String msg) {
                this.code = code;
                this.msg = msg;
            }
        }
    }


    /**
     * PDF内容json格式结果主类
     * 对应JSON文件根数组
     */
    @Data
    public static class ContentItem {
        private MinerUContentType type;           // 元素类型：text/image/table/header/page_number/list/equation
        private String text;           // 文本内容（text/header/page_number/equation类型使用）
        private Integer textLevel;     // 文本层级（text类型，1表示标题级别）
        private List<Integer> bbox;    // 边界框坐标 [x1, y1, x2, y2]
        private Integer pageIdx;       // 页码索引（从0开始）
        private String imgPath;        // 图片路径（image/table类型使用）
        private List<String> imageCaption;   // 图片标题（image类型）
        private List<String> imageFootnote;  // 图片脚注（image类型）
        private String subType;        // 子类型（list类型：text/ref_text）
        private List<String> listItems;      // 列表项内容（list类型）
        private String textFormat;     // 文本格式（equation类型：latex）
        private List<String> tableFootnote; // 表格脚注（table类型）
        private List<String> tableCaption; // 表格标题（table类型）
        private String tableBody; //  表格内容（table类型）

        public static ContentItem copyToText(ContentItem item) {
            ContentItem textItem = new ContentItem();
            textItem.type = MinerUContentType.TEXT;
            textItem.bbox = item.getBbox();
            textItem.pageIdx = item.getPageIdx();
            return textItem;
        }


        @Getter
        public enum MinerUContentType {
            TEXT("text"),
            IMAGE("image"),
            TABLE("table"),
            HEADER("header"),
            PAGE_NUMBER("page_number"),
            LIST("list"),
            EQUATION("equation");

            @JsonValue
            private final String value;

            MinerUContentType(String value) {
                this.value = value;
            }
        }
    }

    /**
     * 轮询结果
     */
    @Data
    public static class PollingResult {
        private boolean success;
        private String info;
        private List<String> zipUrls;
    }
}