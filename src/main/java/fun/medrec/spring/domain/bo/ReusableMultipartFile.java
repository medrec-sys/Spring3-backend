package fun.medrec.spring.domain.bo;

import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 可跨线程复用的 MultipartFile 实现
 * 解决异步处理中 StandardMultipartFile 生命周期问题
 */
public class ReusableMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /**
     * 从原始 MultipartFile 创建可复用的副本
     */
    public ReusableMultipartFile(MultipartFile source) throws IOException {
        this.name = source.getName();
        this.originalFilename = source.getOriginalFilename();
        this.contentType = source.getContentType();
        this.content = source.getBytes();
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte @NotNull [] getBytes() {
        return content;
    }

    @Override
    public @NotNull InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        Files.write(dest.toPath(), content);
    }
}