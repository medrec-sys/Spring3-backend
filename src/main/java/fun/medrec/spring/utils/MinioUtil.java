package fun.medrec.spring.utils;

import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.exception.BusinessException;
import io.minio.*;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;

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
    public static void loadFile(FileData fileData, String objectName) {
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(fileData.getBytes()),
                        fileData.getBytes().length,
                        -1)
                .contentType(fileData.getContentType())
                .build();
        minioClient.putObject(args);
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
