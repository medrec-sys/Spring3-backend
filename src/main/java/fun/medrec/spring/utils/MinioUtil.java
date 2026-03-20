package fun.medrec.spring.utils;

import fun.medrec.spring.exception.BusinessException;
import io.minio.*;
import lombok.SneakyThrows;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public class MinioUtil {
    private static MinioClient minioClient;
    private static String bucketName;
    private static String clientPoint;

    public static void init(MinioClient minioClient, String bucketName, String clientPoint) {
        MinioUtil.minioClient = minioClient;
        MinioUtil.bucketName = bucketName;
        MinioUtil.clientPoint = clientPoint;
    }

    private static Boolean isFileExists(String objectName) {
        try {
            // 通过获取文件元数据来判断文件是否存在
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    public static void loadFile(MultipartFile multipartFile, String objectName) {
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(multipartFile.getInputStream(),
                        multipartFile.getSize(),
                        -1)
                .contentType(multipartFile.getContentType())
                .build();
        minioClient.putObject(args);
    }
    @SneakyThrows
    public static InputStream downLoadFile(String objectName) {
        if (!isFileExists(objectName)) {
            throw new BusinessException("文件不存在");
        }
        GetObjectArgs args = GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build();
        return minioClient.getObject(args);
    }
    public static String getFileUrl(String objectName) {
        return clientPoint + "/" + bucketName + "/" + objectName;
    }

    @SneakyThrows
    public static void deleteFile(String objectName) {
        if (!isFileExists(objectName)) {
            throw new BusinessException("文件不存在");
        }
        RemoveObjectArgs args = RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build();

        minioClient.removeObject(args);
    }

}
