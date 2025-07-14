package com.lifesup.bizstore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import java.time.Duration;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileService {
    private final S3Client s3Client;

    @Value("${bizfly.s3.bucket}")
    private String bucket;

    public FileService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void uploadFile(MultipartFile file) throws IOException {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(file.getOriginalFilename())
                .contentType(file.getContentType())
                .build(),
            software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes())
        );
    }

    public List<String> listFiles() {
        ListObjectsV2Response res = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build());
        return res.contents().stream().map(S3Object::key).collect(Collectors.toList());
    }

    public byte[] downloadFile(String key) {
        return s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build(),
            software.amazon.awssdk.core.sync.ResponseTransformer.toBytes()
        ).asByteArray();
    }

    public List<String> listBuckets() {
        return s3Client.listBuckets().buckets().stream().map(Bucket::name).collect(Collectors.toList());
    }

    public void createBucket(String bucketName) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }

    public void deleteBucket(String bucketName) {
        s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
    }

    public String generatePresignedUrl(String key, int expireSeconds) {
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(s3Client.serviceClientConfiguration().endpointOverride().get())
                .region(s3Client.serviceClientConfiguration().region())
                .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
                .build();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expireSeconds))
                .getObjectRequest(getObjectRequest)
                .build();
        String url = presigner.presignGetObject(presignRequest).url().toString();
        presigner.close();
        return url;
    }

    public void deleteFile(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void renameFile(String oldKey, String newKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(oldKey)
                .destinationBucket(bucket)
                .destinationKey(newKey)
                .build());
        deleteFile(oldKey);
    }
} 